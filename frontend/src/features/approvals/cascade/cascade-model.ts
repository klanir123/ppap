import { canCascade } from '@/lib/permissions'

import type {
  ApprovalCaseDetail,
  ChildCaseRef,
  Completeness,
  DecisionResult,
  SubSupplierCandidate,
  SubSupplierReference,
} from '../api/types'
import { isOpen } from '../model/status'

/** Completeness hint (no blocker): the case has child cases that are not decided yet (TODO(EG)). */
export const SUB_SUPPLIER_PENDING = 'SUB_SUPPLIER_PENDING'

const REFERENCEABLE: readonly DecisionResult[] = ['APPROVED', 'CONDITIONALLY_APPROVED']

/** Server action that allows creating a sub-supplier request (contract §1). */
export const REQUEST_SUB_SUPPLIER = 'REQUEST_SUB_SUPPLIER'

/**
 * "Unterlieferanten-Vorlage anfordern" – the server allows it (`allowedActions`: own role supplier,
 * REQUESTED / IN_PROGRESS) and the user holds both quality roles.
 */
export function canRequestSubSupplier(
  c: Pick<ApprovalCaseDetail, 'ownRole' | 'status' | 'allowedActions'>,
  roles: readonly string[] | undefined,
): boolean {
  return (
    (c.allowedActions as readonly string[]).includes(REQUEST_SUB_SUPPLIER) &&
    c.ownRole === 'SUPPLIER' &&
    (c.status === 'REQUESTED' || c.status === 'IN_PROGRESS') &&
    canCascade(roles)
  )
}

/** Decision result of a candidate (falls back to the case status of decided cases). */
export function candidateResult(c: SubSupplierCandidate): DecisionResult | undefined {
  if (c.decisionResult) return c.decisionResult
  return c.status === 'APPROVED' || c.status === 'CONDITIONALLY_APPROVED' || c.status === 'REJECTED'
    ? c.status
    : undefined
}

/** AK-09-1: only own, decided (approved / conditionally approved) child cases can be referenced. */
export function isReferenceable(c: SubSupplierCandidate): boolean {
  const result = candidateResult(c)
  return c.referenceable !== false && result !== undefined && REFERENCEABLE.includes(result)
}

/** Child case that still blocks the parent (not decided, not cancelled). */
export function isBlockingChild(child: Pick<ChildCaseRef, 'status'>): boolean {
  return isOpen(child.status)
}

export function blockingChildren(children: readonly ChildCaseRef[] | null | undefined): ChildCaseRef[] {
  return (children ?? []).filter(isBlockingChild)
}

/** Candidate ids that are referenced in the draft (by child case id, exchange id or case number). */
export function referencedCandidateIds(
  references: readonly SubSupplierReference[] | null | undefined,
  candidates: readonly SubSupplierCandidate[],
): string[] {
  const refs = references ?? []
  return candidates
    .filter((c) =>
      refs.some(
        (r) =>
          (r.childCaseId && r.childCaseId === c.caseId) ||
          (c.exchangeId && r.exchangeId === c.exchangeId) ||
          r.caseNumber === c.caseNumber,
      ),
    )
    .map((c) => c.caseId)
}

/** The hint arrives in `completeness.hints` (no blocker); older payloads may carry it as an issue. */
export function hasSubSupplierPending(completeness: Completeness | undefined): boolean {
  return [...(completeness?.hints ?? []), ...(completeness?.issues ?? [])].some((i) => i.code === SUB_SUPPLIER_PENDING)
}

/** Element issues without the case-level cascade hint. */
export function elementIssues(completeness: Completeness | undefined): Completeness['issues'] {
  return (completeness?.issues ?? []).filter((i) => i.code !== SUB_SUPPLIER_PENDING)
}
