import {
  EXCHANGE_MODES,
  type ApprovalCaseDetail,
  type CaseRole,
  type CaseStatus,
  type ExchangeMode,
  type SubmissionSummary,
} from '../api/types'

export function latestSubmission(submissions: readonly SubmissionSummary[]): SubmissionSummary | undefined {
  return submissions.reduce<SubmissionSummary | undefined>(
    (latest, s) => (!latest || s.revision > latest.revision ? s : latest),
    undefined,
  )
}

/** Revision to show as "current" in the element overview (`draft` while the supplier works on it). */
export function currentRevisionRef(
  c: Pick<ApprovalCaseDetail, 'submissions' | 'status' | 'ownRole'>,
): number | 'draft' | undefined {
  const latest = latestSubmission(c.submissions)
  if (latest?.status === 'DRAFT') return c.ownRole === 'SUPPLIER' ? 'draft' : undefined
  if (latest) return latest.revision
  return c.ownRole === 'SUPPLIER' && c.status === 'IN_PROGRESS' ? 'draft' : undefined
}

/** Latest revision that carries a decision. */
export function decidedRevision(submissions: readonly SubmissionSummary[]): number | undefined {
  return submissions
    .filter((s) => s.decisionResult)
    .reduce<number | undefined>((max, s) => (max === undefined || s.revision > max ? s.revision : max), undefined)
}

export const isSupplierEditing = (status: CaseStatus, role: CaseRole) => role === 'SUPPLIER' && status === 'IN_PROGRESS'

/** Effective exchange mode of a submitted revision (`submissions[].exchangeMode`); unknown/absent → undefined. */
export function revisionExchangeMode(summary: SubmissionSummary | undefined): ExchangeMode | undefined {
  const mode = summary?.exchangeMode
  return mode && (EXCHANGE_MODES as readonly string[]).includes(mode) ? mode : undefined
}
