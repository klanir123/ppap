import type { DecisionResult, FindingResult } from '../api/types'

export interface FindingDraft {
  result?: FindingResult
  comment?: string
}

export interface DecisionDraft {
  result?: DecisionResult
  conditions: string[]
  conditionDueDate?: string
  comment?: string
}

export type DecisionRuleCode =
  | 'RESULT_REQUIRED'
  /** REJECTED: at least one element NOT_ACCEPTED */
  | 'REJECTION_NEEDS_NOT_ACCEPTED'
  /** REJECTED: comment for every NOT_ACCEPTED element (server: REJECTION_REASON_REQUIRED) */
  | 'REJECTION_REASON_REQUIRED'
  /** CONDITIONALLY_APPROVED: ≥ 1 condition (server: CONDITIONS_REQUIRED) */
  | 'CONDITIONS_REQUIRED'
  | 'CONDITION_DUE_DATE_REQUIRED'
  | 'CONDITION_DUE_DATE_PAST'
  /** APPROVED: no element NOT_ACCEPTED (server: ELEMENTS_NOT_ACCEPTED) */
  | 'ELEMENTS_NOT_ACCEPTED'

export interface DecisionRuleViolation {
  code: DecisionRuleCode
  elementCode?: string
}

export interface DecisionCheck {
  valid: boolean
  errors: DecisionRuleViolation[]
  /** Elements without a finding – allowed, but shown as a hint. */
  unreviewed: string[]
}

/** Client-side mirror of the server rules for POST /decision (server stays authoritative). */
export function checkDecision(
  draft: DecisionDraft,
  findings: Readonly<Record<string, FindingDraft | undefined>>,
  elementCodes: readonly string[],
  today: string,
): DecisionCheck {
  const errors: DecisionRuleViolation[] = []
  const notAccepted = elementCodes.filter((code) => findings[code]?.result === 'NOT_ACCEPTED')
  const unreviewed = elementCodes.filter((code) => !findings[code]?.result)

  switch (draft.result) {
    case undefined:
      errors.push({ code: 'RESULT_REQUIRED' })
      break
    case 'REJECTED':
      if (notAccepted.length === 0) errors.push({ code: 'REJECTION_NEEDS_NOT_ACCEPTED' })
      for (const code of notAccepted) {
        if (!findings[code]?.comment?.trim()) errors.push({ code: 'REJECTION_REASON_REQUIRED', elementCode: code })
      }
      break
    case 'CONDITIONALLY_APPROVED':
      if (!draft.conditions.some((c) => c.trim())) errors.push({ code: 'CONDITIONS_REQUIRED' })
      if (!draft.conditionDueDate) errors.push({ code: 'CONDITION_DUE_DATE_REQUIRED' })
      else if (draft.conditionDueDate <= today) errors.push({ code: 'CONDITION_DUE_DATE_PAST' })
      break
    case 'APPROVED':
      for (const code of notAccepted) errors.push({ code: 'ELEMENTS_NOT_ACCEPTED', elementCode: code })
      break
  }
  return { valid: errors.length === 0, errors, unreviewed }
}

/** Suggested overall result from the element findings. */
export function suggestResult(findings: readonly (FindingDraft | undefined)[]): DecisionResult | undefined {
  const results = findings.map((f) => f?.result)
  if (results.length === 0 || results.some((r) => !r)) return undefined
  if (results.includes('NOT_ACCEPTED')) return 'REJECTED'
  if (results.includes('ACCEPTED_WITH_CONDITION')) return 'CONDITIONALLY_APPROVED'
  return 'APPROVED'
}
