import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

import { setAccessTokenGetter } from '@/api/auth-token'
import type { OidcInfo } from '@/api/generated/model'

export const OIDC_SCOPE = 'openid profile email'

/** State carried through the redirect so the user lands where they started. */
export interface SigninState {
  returnTo?: string
}

export function buildOidcSettings(oidc: OidcInfo, origin = window.location.origin): UserManagerSettings {
  return {
    authority: oidc.authority,
    client_id: oidc.clientId,
    redirect_uri: `${origin}/`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code', // authorization code; oidc-client-ts always uses PKCE (S256)
    scope: OIDC_SCOPE,
    automaticSilentRenew: true,
    loadUserInfo: false,
    // sessionStorage: one session per browser tab – participants can be used side by side
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  }
}

export function createUserManager(oidc: OidcInfo): UserManager {
  const userManager = new UserManager(buildOidcSettings(oidc))
  setAccessTokenGetter(async () => {
    const user = await userManager.getUser()
    return user && !user.expired ? user.access_token : undefined
  })
  return userManager
}

/** Removes `code`/`state` from the URL after the redirect and restores the target path. */
export function onSigninCallback(user: { state?: unknown } | void) {
  const state = (user?.state ?? {}) as SigninState
  const target = state.returnTo?.startsWith('/') ? state.returnTo : window.location.pathname
  window.history.replaceState({}, document.title, target)
}
