import type { ExchangeDirection, ExchangeRecord, ExchangeStep, StepName, StepTarget } from '../api/types'
import { isInFlight, isPullRecord } from './status'

/** One segment of an expected flow: the steps of one target (pulls touch several EDC assets). */
export interface FlowSegment {
  target: StepTarget
  steps: readonly StepName[]
  /** Steps that only appear when recorded (e.g. negotiation skipped by a cached EDR). */
  optional: readonly StepName[]
  /** Whole segment only appears when one of its steps was recorded (e.g. partner key fetch). */
  optionalSegment?: boolean
}

/** Notification sent over EDC (Phase 4). */
export const OUTBOUND_FLOW: readonly StepName[] = ['CATALOG', 'NEGOTIATION', 'TRANSFER', 'EDR', 'DELIVERY']
/** Notification received via the own data plane; KEY_FETCH only when a partner key had to be fetched. */
export const INBOUND_FLOW: readonly StepName[] = ['RECEIPT', 'VALIDATION', 'KEY_FETCH', 'PROCESSING']

const EDC_PRELUDE: readonly StepName[] = ['CATALOG', 'NEGOTIATION', 'TRANSFER', 'EDR']
const CACHED_EDR_OPTIONAL: readonly StepName[] = ['NEGOTIATION', 'TRANSFER']

/**
 * Customer pulls a revision (pull-api.md): DTR (offer → EDR → lookup by ppapExchangeId → shell descriptor),
 * then the submodel asset named in subprotocolBody (offer → EDR → GET → hash → signature → receive);
 * partner keys are only fetched when the signature's kid is unknown.
 */
export const PULL_SUBMISSION_FLOW: readonly FlowSegment[] = [
  { target: 'DTR', steps: [...EDC_PRELUDE, 'DTR_LOOKUP', 'SHELL_DESCRIPTOR'], optional: CACHED_EDR_OPTIONAL },
  {
    target: 'SUBMODEL',
    steps: [...EDC_PRELUDE, 'SUBMODEL_FETCH', 'HASH_CHECK', 'SIGNATURE_CHECK', 'PROCESSING'],
    optional: CACHED_EDR_OPTIONAL,
  },
  { target: 'PUBLIC_KEYS', steps: [...EDC_PRELUDE, 'KEY_FETCH'], optional: EDC_PRELUDE, optionalSegment: true },
]

/** Document pull (both directions): EDR for `ppap-documents-<own BPNL>` → GET /<sha256> → SHA-256 → own storage. */
export const PULL_DOCUMENT_FLOW: readonly FlowSegment[] = [
  {
    target: 'DOCUMENT',
    steps: [...EDC_PRELUDE, 'DOCUMENT_FETCH', 'HASH_CHECK', 'PROCESSING'],
    optional: [...CACHED_EDR_OPTIONAL, 'PROCESSING'],
  },
]

/** Expected flow per record kind (`messageType`) and direction; unknown kinds fall back to the notification flow. */
export function flowTemplate(record: Pick<ExchangeRecord, 'direction' | 'messageType'>): readonly FlowSegment[] {
  switch (record.messageType) {
    case 'PULL_SUBMISSION':
      return PULL_SUBMISSION_FLOW
    case 'PULL_DOCUMENT':
      return PULL_DOCUMENT_FLOW
    case 'TWIN_REGISTRATION':
      return [{ target: 'DTR', steps: ['TWIN_REGISTRATION'], optional: [] }]
    default:
      return [{ target: 'NOTIFICATION', steps: flowFor(record.direction), optional: ['KEY_FETCH'] }]
  }
}

export type StepState = ExchangeStep['status'] | 'PENDING' | 'NOT_REACHED'

export interface TimelineStep {
  step: StepName
  /** Target of the step (only distinguished for pulls; notifications use one segment). */
  target: StepTarget
  state: StepState
  /** Latest recorded attempt of this step (absent for steps not reached yet). */
  entry?: ExchangeStep
  /** Attempts of the step (highest recorded `attempt`). */
  attempts: number
}

export interface TimelineGroup {
  target: StepTarget
  /** Reference within the target (submodel id, document sha256), from the recorded steps. */
  targetRef?: string
  steps: TimelineStep[]
}

export function flowFor(direction: ExchangeDirection): readonly StepName[] {
  return direction === 'OUTBOUND' ? OUTBOUND_FLOW : INBOUND_FLOW
}

/**
 * Target of a recorded step within a pull. Steps without a specific target arrive as NOTIFICATION (the server
 * default) and are attributed by step type.
 */
function pullTarget(messageType: string, entry: ExchangeStep): StepTarget {
  if (entry.target && entry.target !== 'NOTIFICATION') return entry.target
  if (entry.step === 'KEY_FETCH') return 'PUBLIC_KEYS'
  if (messageType === 'PULL_DOCUMENT') return 'DOCUMENT'
  if (entry.step === 'DTR_LOOKUP' || entry.step === 'SHELL_DESCRIPTOR') return 'DTR'
  return 'SUBMODEL'
}

/**
 * Merges the recorded steps into the expected flow: one row per step and target (latest attempt wins),
 * steps not reached yet are PENDING while the record is in flight, otherwise NOT_REACHED.
 * Unknown/extra steps are appended in recorded order.
 */
export function buildTimeline(
  record: Pick<ExchangeRecord, 'direction' | 'status' | 'steps'> & { messageType?: string },
): TimelineStep[] {
  const messageType = record.messageType ?? ''
  const segments = flowTemplate({ direction: record.direction, messageType })
  const pull = isPullRecord(messageType)
  const targetOf = (entry: ExchangeStep): StepTarget =>
    pull ? pullTarget(messageType, entry) : (segments[0]?.target ?? 'NOTIFICATION')
  const keyOf = (target: StepTarget, step: StepName) => `${target}:${step}`

  const recorded = new Map<string, { entry: ExchangeStep; target: StepTarget; count: number; maxAttempt: number }>()
  const order: string[] = []
  for (const entry of [...record.steps].sort(
    (a, b) => a.at.localeCompare(b.at) || (a.attempt ?? 0) - (b.attempt ?? 0),
  )) {
    const target = targetOf(entry)
    const key = keyOf(target, entry.step)
    const existing = recorded.get(key)
    if (!existing) order.push(key)
    recorded.set(key, {
      entry,
      target,
      count: (existing?.count ?? 0) + 1,
      maxAttempt: Math.max(existing?.maxAttempt ?? 0, entry.attempt ?? 0),
    })
  }
  const attemptsOf = (r: { count: number; maxAttempt: number }) => (r.maxAttempt > 0 ? r.maxAttempt : r.count)

  const missingState: StepState = isInFlight(record.status) ? 'PENDING' : 'NOT_REACHED'
  const rows: TimelineStep[] = []
  const expected = new Set<string>()
  for (const segment of segments) {
    const keys = segment.steps.map((step) => keyOf(segment.target, step))
    keys.forEach((k) => expected.add(k))
    if (segment.optionalSegment && !keys.some((k) => recorded.has(k))) continue
    segment.steps.forEach((step, i) => {
      const found = recorded.get(keys[i]!)
      if (found) {
        rows.push({
          step,
          target: segment.target,
          state: found.entry.status,
          entry: found.entry,
          attempts: attemptsOf(found),
        })
      } else if (!segment.optional.includes(step)) {
        rows.push({ step, target: segment.target, state: missingState, attempts: 0 })
      }
    })
  }
  for (const key of order) {
    if (expected.has(key)) continue
    const found = recorded.get(key)!
    rows.push({
      step: found.entry.step,
      target: found.target,
      state: found.entry.status,
      entry: found.entry,
      attempts: attemptsOf(found),
    })
  }
  return rows
}

/** Contiguous rows of the same target → groups (pull records show one block per DTR / submodel / document). */
export function groupTimeline(rows: readonly TimelineStep[]): TimelineGroup[] {
  const groups: TimelineGroup[] = []
  for (const row of rows) {
    const last = groups[groups.length - 1]
    const group = last && last.target === row.target ? last : undefined
    if (group) group.steps.push(row)
    else groups.push({ target: row.target, steps: [row] })
    const ref = row.entry?.targetRef
    const current = groups[groups.length - 1]!
    if (ref && !current.targetRef) current.targetRef = ref
  }
  return groups
}

/** Total duration of all recorded steps (ms). */
export function totalDuration(steps: readonly ExchangeStep[]): number | undefined {
  const values = steps.map((s) => s.durationMs).filter((v): v is number => typeof v === 'number')
  return values.length ? values.reduce((a, b) => a + b, 0) : undefined
}
