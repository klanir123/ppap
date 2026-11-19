import { CASE_STATUSES, type ApprovalCase, type CaseStatus } from '../api/types'

export interface KanbanColumn<T> {
  status: CaseStatus
  cases: T[]
}

/** Board columns: the workflow order; cancelled cases only when requested or filtered for. */
export function boardStatuses(filter: readonly CaseStatus[] | undefined, includeCancelled = false): CaseStatus[] {
  if (filter?.length) return CASE_STATUSES.filter((s) => filter.includes(s))
  return CASE_STATUSES.filter((s) => includeCancelled || s !== 'CANCELLED')
}

/**
 * Groups cases into status columns (read-only board). Within a column overdue cases come first,
 * then by due date ascending (missing due date last), then by case number.
 */
export function groupByStatus<T extends Pick<ApprovalCase, 'status' | 'dueDate' | 'overdue' | 'caseNumber'>>(
  cases: readonly T[],
  statuses: readonly CaseStatus[],
): KanbanColumn<T>[] {
  const columns = new Map<CaseStatus, T[]>(statuses.map((s) => [s, []]))
  for (const c of cases) columns.get(c.status)?.push(c)
  for (const list of columns.values()) {
    list.sort(
      (a, b) =>
        Number(b.overdue) - Number(a.overdue) ||
        (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') ||
        a.caseNumber.localeCompare(b.caseNumber, undefined, { numeric: true }),
    )
  }
  return statuses.map((status) => ({ status, cases: columns.get(status) ?? [] }))
}
