import type { QueryKey } from '@tanstack/react-query'

import { CASES_URL, EXCHANGE_URL, TWINS_URL } from '../api/client'
import type { LiveEvent } from '../api/types'
import type { SseMessage } from './sse'

const AUDIT_PREFIX = '/api/v1/audit-events'
const STATS_KEY = `${CASES_URL}/stats`

/** SSE message → typed event (`event:` field, or `type` in the JSON data); heartbeats and unknown events → undefined. */
export function parseLiveEvent(message: SseMessage): LiveEvent | undefined {
  let data: Record<string, unknown>
  try {
    data = JSON.parse(message.data) as Record<string, unknown>
  } catch {
    return undefined
  }
  if (typeof data !== 'object' || data === null) return undefined
  const type = message.event !== 'message' ? message.event : data.type
  if (type === 'case.changed' && typeof data.caseId === 'string') {
    return { ...data, type, caseId: data.caseId } as LiveEvent
  }
  if (type === 'exchange.changed') return { ...data, type } as LiveEvent
  return undefined
}

function isFinished(status: string | undefined): boolean {
  return status === 'PROCESSED' || status === 'DELIVERED' || status === 'FAILED' || status === 'REJECTED'
}

function firstKey(key: QueryKey): string | undefined {
  return typeof key[0] === 'string' ? key[0] : undefined
}

/**
 * Which cached queries an event makes stale:
 * - `case.changed`: case lists (incl. clarification count), stats, the case detail and everything below it, audit trail
 * - `exchange.changed`: exchange records of the case + cockpit queries (messages, status) + digital twins;
 *   once a record of the case is finished, also the case and its revisions (document transfer states of pulls)
 */
export function affectsQuery(event: LiveEvent, queryKey: QueryKey): boolean {
  const key = firstKey(queryKey)
  if (!key) return false
  if (event.type === 'case.changed') {
    return (
      key === CASES_URL ||
      key === STATS_KEY ||
      key === `${CASES_URL}/${event.caseId}` ||
      key.startsWith(`${CASES_URL}/${event.caseId}/`) ||
      key.startsWith(AUDIT_PREFIX)
    )
  }
  if (key.startsWith(EXCHANGE_URL) || key.startsWith(TWINS_URL)) return true
  if (!event.caseId) return /\/exchange$/.test(key)
  if (key === `${CASES_URL}/${event.caseId}/exchange`) return true
  return (
    isFinished(event.status) &&
    (key === `${CASES_URL}/${event.caseId}` || key.startsWith(`${CASES_URL}/${event.caseId}/submissions`))
  )
}

/** After a reconnect events may have been missed: refresh all case and exchange data. */
export function affectedByResume(queryKey: QueryKey): boolean {
  const key = firstKey(queryKey)
  return Boolean(
    key &&
    (key.startsWith(CASES_URL) ||
      key.startsWith(EXCHANGE_URL) ||
      key.startsWith(TWINS_URL) ||
      key.startsWith(AUDIT_PREFIX)),
  )
}
