import type { AuthContextProps } from 'react-oidc-context'

import type { SigninState } from '@/features/auth/oidc'

import type { DecisionDraft } from '../model/decision-rules'

const key = (caseId: string) => `ppap.pendingDecision.${caseId}`

/** Pending decision survives the re-login redirect (sessionStorage = this tab only). */
export function savePendingDecision(caseId: string, draft: DecisionDraft) {
  try {
    window.sessionStorage.setItem(key(caseId), JSON.stringify({ draft, savedAt: Date.now() }))
  } catch {
    // storage unavailable – the user has to re-enter the decision
  }
}

/** Pending draft for this case (max. 30 minutes old), without removing it. */
export function peekPendingDecision(caseId: string, now = Date.now()): DecisionDraft | undefined {
  try {
    const raw = window.sessionStorage.getItem(key(caseId))
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as { draft: DecisionDraft; savedAt: number }
    return now - parsed.savedAt < 30 * 60_000 ? parsed.draft : undefined
  } catch {
    return undefined
  }
}

export function clearPendingDecision(caseId: string) {
  try {
    window.sessionStorage.removeItem(key(caseId))
  } catch {
    // ignore
  }
}

/**
 * Server demands a fresh login (401 RECENT_AUTH_REQUIRED): redirect to the IdP with `max_age=0`
 * and `prompt=login`; afterwards the user returns to the review page and confirms again.
 */
export function requestRecentAuth(auth: Pick<AuthContextProps, 'signinRedirect'>) {
  const state: SigninState = { returnTo: window.location.pathname + window.location.search }
  return auth.signinRedirect({ state, max_age: 0, prompt: 'login' })
}
