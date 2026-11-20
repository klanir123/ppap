import type { ConnectorLogEvent, ExchangeChangedEvent } from '@/features/exchange/api/types'

import type { DataspaceMessage } from '../api'

/** Message categories of the legend (contract §6): request, status, package / provision, pull, decision. */
export const FLOW_CATEGORIES = ['request', 'status', 'package', 'pull', 'decision'] as const
export type FlowCategory = (typeof FLOW_CATEGORIES)[number]

const CATEGORY_BY_TYPE: Record<string, FlowCategory> = {
  PPAP_REQUEST: 'request',
  PPAP_REQUEST_WITHDRAWN: 'request',
  PPAP_STATUS_UPDATE: 'status',
  PPAP_SUBMISSION: 'package',
  PPAP_SUBMISSION_AVAILABLE: 'package',
  PULL_SUBMISSION: 'pull',
  PULL_DOCUMENT: 'pull',
  PPAP_DECISION: 'decision',
}

/** Category of a record kind; local kinds (twin registration) have none → no animation along an edge. */
export function flowCategory(messageType: string): FlowCategory | undefined {
  return CATEGORY_BY_TYPE[messageType]
}

/** CSS colour token per category (index.css `--flow-*`, light + dark). */
export function flowColor(category: FlowCategory): string {
  return `var(--flow-${category})`
}

/**
 * Which way the data travels. Notifications: outbound = own connector → partner, inbound = partner → own.
 * Pulls are recorded by the party that pulls: the data travels from the partner to the own connector.
 */
export function flowDirection(m: Pick<DataspaceMessage, 'direction' | 'messageType'>): 'toPartner' | 'toSelf' {
  if (flowCategory(m.messageType) === 'pull') return 'toSelf'
  return m.direction === 'OUTBOUND' ? 'toPartner' : 'toSelf'
}

export interface FlowAnimation {
  /** Unique per animation (message id + start). */
  key: string
  messageId: string
  partnerBpnl: string
  category: FlowCategory
  direction: 'toPartner' | 'toSelf'
  messageType: string
  caseNumber?: string | null
  /** performance.now()-based start. */
  start: number
  duration: number
}

export const FLOW_DURATION_MS = 2200

/** Message → animation (undefined for local kinds or partners without an edge). */
export function toAnimation(
  m: Pick<DataspaceMessage, 'id' | 'direction' | 'messageType' | 'partner' | 'caseNumber'>,
  start: number,
  knownPartners: ReadonlySet<string>,
  duration = FLOW_DURATION_MS,
): FlowAnimation | undefined {
  const category = flowCategory(m.messageType)
  const partnerBpnl = m.partner.bpnl
  if (!category || !partnerBpnl || !knownPartners.has(partnerBpnl)) return undefined
  return {
    key: `${m.id}@${Math.round(start)}`,
    messageId: m.id,
    partnerBpnl,
    category,
    direction: flowDirection(m),
    messageType: m.messageType,
    caseNumber: m.caseNumber,
    start,
    duration,
  }
}

/**
 * Live event → minimal message: `connector.log` (first step of a record) carries direction, kind and partner;
 * `exchange.changed` only if enriched. Undefined when the event lacks the details.
 */
export function messageFromEvent(
  e: ExchangeChangedEvent | ConnectorLogEvent,
): Pick<DataspaceMessage, 'id' | 'direction' | 'messageType' | 'partner' | 'caseNumber' | 'caseId'> | undefined {
  if (!e.messageId || !e.messageType || !e.direction || !e.partnerBpnl) return undefined
  return {
    id: e.messageId,
    direction: e.direction,
    messageType: e.messageType,
    partner: { bpnl: e.partnerBpnl },
    caseNumber: 'caseNumber' in e ? (e.caseNumber ?? undefined) : undefined,
    caseId: e.caseId ?? undefined,
  }
}

/** Progress 0..1 of an animation at `now` (clamped); eased for a smooth arrival. */
export function progress(a: Pick<FlowAnimation, 'start' | 'duration'>, now: number): number {
  const t = Math.min(1, Math.max(0, (now - a.start) / a.duration))
  return t < 0.5 ? 2 * t * t : 1 - (-2 * t + 2) ** 2 / 2
}

export const REPLAY_WINDOW_MS = 15 * 60_000
export const REPLAY_DURATION_MS = 30_000
const REPLAY_MIN_GAP_MS = 1_100
const REPLAY_LEAD_IN_MS = 400

/**
 * Time-lapse of the last 15 minutes: messages in the window, oldest first, mapped onto `durationMs`
 * (15 min → durationMs, starting with the first message after a short lead-in; at least
 * `REPLAY_MIN_GAP_MS` apart so bursts stay readable).
 */
export function replaySchedule<T extends Pick<DataspaceMessage, 'at'>>(
  messages: readonly T[],
  now: number,
  windowMs = REPLAY_WINDOW_MS,
  durationMs = REPLAY_DURATION_MS,
): { message: T; offset: number }[] {
  const from = now - windowMs
  const inWindow = messages
    .filter((m) => {
      const at = Date.parse(m.at)
      return at >= from && at <= now
    })
    .sort((a, b) => Date.parse(a.at) - Date.parse(b.at))
  let last = -Infinity
  const first = inWindow.length ? Date.parse(inWindow[0]!.at) : from
  return inWindow.map((message) => {
    const scaled = REPLAY_LEAD_IN_MS + ((Date.parse(message.at) - first) / windowMs) * durationMs
    const offset = Math.max(scaled, last + REPLAY_MIN_GAP_MS)
    last = offset
    return { message, offset: Math.round(offset) }
  })
}
