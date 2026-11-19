import type { ApprovalCaseDetail, CaseStatus } from '@/features/approvals/api/types'

import type { LiveEvent } from '../api/types'

export type LiveNotification =
  | { kind: 'newRequest'; caseId: string; caseNumber: string; partner: string }
  | { kind: 'submissionReceived'; caseId: string; caseNumber: string; partner: string }
  | { kind: 'decisionReceived'; caseId: string; caseNumber: string; partner: string; result: CaseStatus }

const DECISIONS: readonly CaseStatus[] = ['APPROVED', 'CONDITIONALLY_APPROVED', 'REJECTED']

/** Statuses worth a toast – only these trigger a detail fetch. */
export function mayNotify(event: LiveEvent): event is Extract<LiveEvent, { type: 'case.changed' }> {
  return (
    event.type === 'case.changed' &&
    (event.status === 'REQUESTED' || event.status === 'SUBMITTED' || DECISIONS.includes(event.status as CaseStatus))
  )
}

/**
 * Toast for changes that came from the partner (never for own actions):
 * supplier – new request received / decision received; customer – submission received.
 */
export function notificationFor(c: ApprovalCaseDetail): LiveNotification | undefined {
  const base = { caseId: c.id, caseNumber: c.caseNumber, partner: c.partner.name ?? c.partner.bpnl }
  if (c.ownRole === 'SUPPLIER' && c.status === 'REQUESTED' && c.source === 'EXCHANGE') {
    return { kind: 'newRequest', ...base }
  }
  if (c.ownRole === 'SUPPLIER' && DECISIONS.includes(c.status) && c.source !== 'MANUAL') {
    return { kind: 'decisionReceived', ...base, result: c.status }
  }
  if (c.ownRole === 'CUSTOMER' && c.status === 'SUBMITTED') {
    return { kind: 'submissionReceived', ...base }
  }
  return undefined
}
