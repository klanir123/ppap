import { z } from 'zod'

import { DEFAULT_PAGE_SIZE, listSearchSchema } from '@/components/data-table'

import { CASE_ROLES, CASE_STATUSES, type CaseListParams } from '../api/types'

export const APPROVAL_VIEWS = ['table', 'board'] as const
export type ApprovalView = (typeof APPROVAL_VIEWS)[number]

/** `/approvals` search params: list state + filters + view (table | board). */
export const approvalsSearchSchema = listSearchSchema.extend({
  view: z.enum(APPROVAL_VIEWS).optional().catch(undefined),
  status: z.array(z.enum(CASE_STATUSES)).optional().catch(undefined),
  role: z.enum(CASE_ROLES).optional().catch(undefined),
  partner: z.string().optional().catch(undefined),
  overdue: z.boolean().optional().catch(undefined),
})
export type ApprovalsSearch = z.infer<typeof approvalsSearchSchema>

export const DEFAULT_SORT = 'dueDate,asc'
/** Board loads one large page; beyond that a hint asks to filter. */
export const BOARD_PAGE_SIZE = 200

export function toCaseListParams(search: ApprovalsSearch, view: ApprovalView): CaseListParams {
  return {
    q: search.q || undefined,
    status: search.status?.length ? search.status : undefined,
    ownRole: search.role,
    partnerBpnl: search.partner,
    overdue: search.overdue || undefined,
    page: view === 'board' ? 0 : (search.page ?? 0),
    size: view === 'board' ? BOARD_PAGE_SIZE : (search.size ?? DEFAULT_PAGE_SIZE),
    sort: search.sort ?? DEFAULT_SORT,
  }
}

export function hasFilters(search: ApprovalsSearch): boolean {
  return Boolean(search.q || search.status?.length || search.role || search.partner || search.overdue)
}
