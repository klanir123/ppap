import type { AuthContextProps } from 'react-oidc-context'

import type { SigninState } from './oidc'

/** Starts the redirect to the identity provider and remembers the current location. */
export function signinWithReturn(auth: Pick<AuthContextProps, 'signinRedirect'>) {
  const state: SigninState = { returnTo: window.location.pathname + window.location.search }
  return auth.signinRedirect({ state })
}
