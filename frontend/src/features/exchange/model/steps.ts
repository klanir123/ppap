import type { ExchangeDirection, ExchangeRecord, ExchangeStep, StepName } from '../api/types'
import { isInFlight } from './status'

/** Regular flow per direction; KEY_FETCH only appears when a partner key had to be fetched. */
export const OUTBOUND_FLOW: readonly StepName[] = ['CATALOG', 'NEGOTIATION', 'TRANSFER', 'EDR', 'DELIVERY']
export const INBOUND_FLOW: readonly StepName[] = ['RECEIPT', 'VALIDATION', 'KEY_FETCH', 'PROCESSING']
const OPTIONAL_STEPS: readonly StepName[] = ['KEY_FETCH']

export type StepState = ExchangeStep['status'] | 'PENDING' | 'NOT_REACHED'

export interface TimelineStep {
  step: StepName
  state: StepState
  /** Latest recorded attempt of this step (absent for steps not reached yet). */
  entry?: ExchangeStep
  /** How often the step was recorded (retries). */
  attempts: number
}

export function flowFor(direction: ExchangeDirection): readonly StepName[] {
  return direction === 'OUTBOUND' ? OUTBOUND_FLOW : INBOUND_FLOW
}

/**
 * Merges the recorded steps into the expected flow: one row per step (latest attempt wins),
 * steps not reached yet are PENDING while the message is in flight, otherwise NOT_REACHED.
 * Unknown/extra steps are appended in recorded order.
 */
export function buildTimeline(record: Pick<ExchangeRecord, 'direction' | 'status' | 'steps'>): TimelineStep[] {
  const flow = flowFor(record.direction)
  const byStep = new Map<StepName, { entry: ExchangeStep; attempts: number }>()
  const order: StepName[] = []
  for (const entry of [...record.steps].sort(
    (a, b) => a.at.localeCompare(b.at) || (a.attempt ?? 0) - (b.attempt ?? 0),
  )) {
    const existing = byStep.get(entry.step)
    if (!existing) order.push(entry.step)
    byStep.set(entry.step, { entry, attempts: Math.max((existing?.attempts ?? 0) + 1, entry.attempt ?? 0) })
  }
  const missingState: StepState = isInFlight(record.status) ? 'PENDING' : 'NOT_REACHED'
  const rows: TimelineStep[] = []
  for (const step of flow) {
    const found = byStep.get(step)
    if (found) rows.push({ step, state: found.entry.status, entry: found.entry, attempts: found.attempts })
    else if (!OPTIONAL_STEPS.includes(step)) rows.push({ step, state: missingState, attempts: 0 })
  }
  for (const step of order) {
    if (flow.includes(step)) continue
    const found = byStep.get(step)!
    rows.push({ step, state: found.entry.status, entry: found.entry, attempts: found.attempts })
  }
  return rows
}

/** Total duration of all recorded steps (ms). */
export function totalDuration(steps: readonly ExchangeStep[]): number | undefined {
  const values = steps.map((s) => s.durationMs).filter((v): v is number => typeof v === 'number')
  return values.length ? values.reduce((a, b) => a + b, 0) : undefined
}
