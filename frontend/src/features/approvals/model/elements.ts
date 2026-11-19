import type { Completeness, RequestElement, SubmissionElement } from '../api/types'

export type ElementState = 'complete' | 'issues' | 'open' | 'optional'

/** Visual state of an element in the package workspace. */
export function elementState(
  requirement: RequestElement['requirement'],
  element: SubmissionElement | undefined,
  issueCount: number,
): ElementState {
  const touched = Boolean(
    element &&
    (element.selfAssessment || element.comment || element.documents.length > 0 || element.values !== undefined),
  )
  if (issueCount > 0 && (requirement === 'REQUIRED' || touched)) return 'issues'
  if (touched && (element?.complete ?? issueCount === 0)) return 'complete'
  return requirement === 'REQUIRED' ? 'open' : 'optional'
}

/** Completeness issues grouped by element code. */
export function issuesByElement(completeness: Completeness | undefined): Map<string, Completeness['issues']> {
  const map = new Map<string, Completeness['issues']>()
  for (const issue of completeness?.issues ?? []) {
    const list = map.get(issue.elementCode) ?? []
    list.push(issue)
    map.set(issue.elementCode, list)
  }
  return map
}

export const needsComment = (assessment: SubmissionElement['selfAssessment'] | null) =>
  assessment === 'NOK' || assessment === 'NA'

export const needsDocuments = (evidenceType: string) =>
  evidenceType === 'DOCUMENT' || evidenceType === 'DOCUMENT_AND_VALUES'
export const hasValues = (evidenceType: string) => evidenceType === 'VALUES' || evidenceType === 'DOCUMENT_AND_VALUES'
