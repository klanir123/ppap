import {
  NOTIFICATION_TYPES,
  PULL_KINDS,
  type ExchangeRecord,
  type ExchangeStatus,
  type LastOutbound,
  type MessageType,
} from '../api/types'

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

/** Notification (EDC message) – as opposed to pull records and the local twin registration. */
export function isNotification(messageType: string): boolean {
  return (NOTIFICATION_TYPES as readonly string[]).includes(messageType)
}

export function isPullRecord(messageType: string): boolean {
  return (PULL_KINDS as readonly string[]).includes(messageType)
}

/**
 * Delivery indicator: state of the latest outbound record of the given kind(s);
 * without kinds, of the latest outbound notification (twin registrations are no deliveries).
 */
export function deriveDelivery(
  records: readonly ExchangeRecord[] | undefined,
  messageType?: MessageType | readonly MessageType[],
): DeliverySummary | undefined {
  const kinds = messageType === undefined ? undefined : ([] as readonly string[]).concat(messageType)
  const outbound = (records ?? []).filter(
    (r) =>
      r.direction === 'OUTBOUND' &&
      (kinds === undefined ? isNotification(r.messageType) : kinds.includes(r.messageType)),
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
