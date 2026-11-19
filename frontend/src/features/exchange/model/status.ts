import type { ExchangeRecord, ExchangeStatus, LastOutbound, MessageType } from '../api/types'

/** Message still on its way (outbound) or not yet processed (inbound). */
export function isInFlight(status: ExchangeStatus): boolean {
  return status === 'PENDING' || status === 'IN_PROGRESS' || status === 'RECEIVED'
}

export function isFailure(status: ExchangeStatus): boolean {
  return status === 'FAILED' || status === 'REJECTED'
}

/** Retry is offered for failed outbound and rejected inbound messages (contract: POST …/retry). */
export function canRetry(record: Pick<ExchangeRecord, 'status' | 'direction' | 'retryable'>): boolean {
  if (record.retryable !== undefined) return record.retryable
  return record.direction === 'OUTBOUND' ? record.status === 'FAILED' : record.status === 'REJECTED'
}

/** Newest first (createdAt desc, then id for a stable order). */
export function sortNewestFirst<T extends Pick<ExchangeRecord, 'createdAt' | 'id'>>(records: readonly T[]): T[] {
  return [...records].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id))
}

export type DeliveryState = 'delivered' | 'pending' | 'failed'

export interface DeliverySummary {
  state: DeliveryState
  record: ExchangeRecord
}

/** Delivery indicator: state of the latest outbound message (optionally of one message type). */
export function deriveDelivery(
  records: readonly ExchangeRecord[] | undefined,
  messageType?: MessageType,
): DeliverySummary | undefined {
  const outbound = (records ?? []).filter(
    (r) => r.direction === 'OUTBOUND' && (messageType === undefined || r.messageType === messageType),
  )
  const latest = sortNewestFirst(outbound)[0]
  if (!latest) return undefined
  const state: DeliveryState =
    latest.status === 'DELIVERED' ? 'delivered' : latest.status === 'FAILED' ? 'failed' : 'pending'
  return { state, record: latest }
}

/** Delivery state from the case DTO summary (`lastOutbound`, list rows) – same mapping as {@link deriveDelivery}. */
export function deliveryFromLastOutbound(last: LastOutbound | null | undefined): DeliveryState | undefined {
  if (!last) return undefined
  return last.status === 'DELIVERED' ? 'delivered' : last.status === 'FAILED' ? 'failed' : 'pending'
}
