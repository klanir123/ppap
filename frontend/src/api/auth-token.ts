/**
 * Bridge between the OIDC layer and the non-React fetch mutator.
 * The auth layer registers a getter once the UserManager exists.
 */
type TokenGetter = () => string | undefined | Promise<string | undefined>

let getter: TokenGetter = () => undefined

export function setAccessTokenGetter(fn: TokenGetter) {
  getter = fn
}

export async function getAccessToken(): Promise<string | undefined> {
  return getter()
}
