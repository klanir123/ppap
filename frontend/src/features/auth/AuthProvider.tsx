import { useMemo, type ReactNode } from 'react'
import { AuthProvider as OidcAuthProvider } from 'react-oidc-context'

import { useAppConfig } from '@/features/config/config-context'

import { createUserManager, onSigninCallback } from './oidc'

/** OIDC (authorization code + PKCE) bootstrapped from the runtime config. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const { oidc } = useAppConfig()
  const userManager = useMemo(() => createUserManager(oidc), [oidc])
  return (
    <OidcAuthProvider userManager={userManager} onSigninCallback={onSigninCallback}>
      {children}
    </OidcAuthProvider>
  )
}
