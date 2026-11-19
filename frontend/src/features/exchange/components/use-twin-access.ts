import type { ApprovalCaseDetail } from '@/features/approvals/api/types'
import { useCurrentUser } from '@/features/auth/current-user'
import { hasPermission } from '@/lib/permissions'

/** Twin diagnostics are a supplier concern (own DTR) for quality managers and connector admins. */
export function useCanViewTwin(c: Pick<ApprovalCaseDetail, 'ownRole' | 'source'>): boolean {
  const { roles } = useCurrentUser()
  return c.ownRole === 'SUPPLIER' && c.source !== 'MANUAL' && hasPermission(roles, 'twins:view')
}
