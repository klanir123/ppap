import { createContext, useContext } from 'react'

import type { MeResponse } from '@/api/generated/model'
import { hasPermission, type Permission } from '@/lib/permissions'

export const CurrentUserContext = createContext<MeResponse | null>(null)

export function useCurrentUser(): MeResponse {
  const me = useContext(CurrentUserContext)
  if (!me) throw new Error('useCurrentUser must be used within <AuthGate>')
  return me
}

export function usePermission(permission: Permission): boolean {
  return hasPermission(useCurrentUser().roles, permission)
}

/** displayName and username are optional in the contract – fall back sensibly. */
export function userDisplayName(me: Pick<MeResponse, 'displayName' | 'username' | 'email' | 'userId'>): string {
  return me.displayName?.trim() || me.username || me.email || me.userId
}
