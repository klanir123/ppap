import type { ApprovalStatus } from '@/lib/status'

import type { CaseStatus } from '../api/types'

/** API status (IN_PROGRESS) → design token (in-progress). */
export function caseStatusToken(status: CaseStatus): ApprovalStatus {
  return status.toLowerCase().replace(/_/g, '-') as ApprovalStatus
}

/** Statuses in which the case is still being worked on (not decided or cancelled). */
export const OPEN_STATUSES: readonly CaseStatus[] = ['DRAFT', 'REQUESTED', 'IN_PROGRESS', 'SUBMITTED', 'IN_REVIEW']
export const CLOSED_STATUSES: readonly CaseStatus[] = ['APPROVED', 'CONDITIONALLY_APPROVED', 'REJECTED', 'CANCELLED']

export function isOpen(status: CaseStatus): boolean {
  return OPEN_STATUSES.includes(status)
}

/** Local date (YYYY-MM-DD) without time zone shift. */
export function todayIso(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** Days from today until the due date (negative = overdue). */
export function daysUntil(dueDate: string, now = new Date()): number {
  const [y, m, d] = dueDate.slice(0, 10).split('-').map(Number)
  const due = Date.UTC(y!, m! - 1, d!)
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((due - today) / 86_400_000)
}
