import { hasPermission, type Permission } from '@/lib/permissions'

import { CASE_ACTIONS, type ApprovalCase, type CaseAction } from '../api/types'

/** UI permission(s) an action needs in addition to the server's `allowedActions` (any of). */
const ACTION_PERMISSIONS: Record<CaseAction, readonly Permission[]> = {
  EDIT: ['approvals:request'],
  SEND: ['approvals:request'],
  // customers cancel their drafts/requests, suppliers their manually captured requests
  CANCEL: ['approvals:request', 'approvals:supply'],
  START_WORK: ['approvals:supply'],
  EDIT_SUBMISSION: ['approvals:supply'],
  SUBMIT: ['approvals:supply'],
  NEW_REVISION: ['approvals:supply'],
  START_REVIEW: ['approvals:decide'],
  REVIEW: ['approvals:decide'],
  DECIDE: ['approvals:decide'],
}

function isCaseAction(value: string): value is CaseAction {
  return (CASE_ACTIONS as readonly string[]).includes(value)
}

/**
 * Actions to offer: allowed by the server for this case AND covered by a UI permission of the user.
 * The server stays authoritative; unknown action names are ignored. Order = CASE_ACTIONS.
 */
export function permittedActions(
  allowedActions: readonly string[],
  roles: readonly string[] | undefined,
): CaseAction[] {
  const allowed = new Set(allowedActions.filter(isCaseAction))
  return CASE_ACTIONS.filter(
    (action) => allowed.has(action) && ACTION_PERMISSIONS[action].some((p) => hasPermission(roles, p)),
  )
}

export type Workspace = 'package' | 'review'

/** Which workspace (if any) the user can open for this case. */
export function availableWorkspace(
  c: Pick<ApprovalCase, 'status' | 'ownRole'>,
  actions: readonly CaseAction[],
): Workspace | undefined {
  if (
    c.ownRole === 'SUPPLIER' &&
    c.status === 'IN_PROGRESS' &&
    actions.some((a) => a === 'SUBMIT' || a === 'EDIT_SUBMISSION')
  )
    return 'package'
  if (
    c.ownRole === 'CUSTOMER' &&
    (c.status === 'SUBMITTED' || c.status === 'IN_REVIEW') &&
    actions.some((a) => a === 'START_REVIEW' || a === 'REVIEW' || a === 'DECIDE')
  )
    return 'review'
  return undefined
}
