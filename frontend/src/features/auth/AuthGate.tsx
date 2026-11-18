import { useQueryClient } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from 'react-oidc-context'

import { useGetMe } from '@/api/generated/endpoints/identity/identity'
import { isApiError } from '@/api/problem'
import { ErrorScreen } from '@/features/errors/ErrorScreen'
import { FullPageLoader } from '@/features/errors/FullPageLoader'

import { CurrentUserContext } from './current-user'
import { LoginPage } from './LoginPage'
import { signinWithReturn } from './signin'

/**
 * Shows the login landing page for anonymous users (no auto-redirect loop),
 * otherwise loads `/api/v1/me` and provides it to the app.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const { t } = useTranslation('auth')

  if (auth.activeNavigator === 'signoutRedirect') return <FullPageLoader label={t('signingOut')} />
  if (auth.isLoading || auth.activeNavigator) return <FullPageLoader label={t('signingIn')} />
  if (auth.error) {
    return (
      <ErrorScreen
        title={t('error.title')}
        description={t('error.description')}
        error={auth.error}
        onRetry={() => void signinWithReturn(auth)}
        retryLabel={t('login.button')}
      />
    )
  }
  if (!auth.isAuthenticated) return <LoginPage onLogin={() => void signinWithReturn(auth)} />
  return <CurrentUserGate>{children}</CurrentUserGate>
}

function CurrentUserGate({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const { t } = useTranslation('auth')
  const queryClient = useQueryClient()
  const me = useGetMe({
    query: { staleTime: 5 * 60_000, retry: (count, error) => !isUnauthorized(error) && count < 2 },
  })

  // Drop cached server state when the signed-in user changes or signs out.
  useEffect(() => auth.events.addUserSignedOut(() => queryClient.clear()), [auth.events, queryClient])

  if (me.isPending) return <FullPageLoader />
  if (me.isError) {
    if (isUnauthorized(me.error)) {
      return (
        <ErrorScreen
          title={t('error.title')}
          description={t('sessionExpired')}
          error={me.error}
          onRetry={() => void auth.removeUser().then(() => signinWithReturn(auth))}
          retryLabel={t('login.button')}
        />
      )
    }
    return <ErrorScreen error={me.error} onRetry={() => void me.refetch()} />
  }
  return <CurrentUserContext value={me.data}>{children}</CurrentUserContext>
}

function isUnauthorized(error: unknown) {
  return isApiError(error) && error.status === 401
}
