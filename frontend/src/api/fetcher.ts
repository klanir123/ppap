import { getAccessToken } from './auth-token'
import { ApiError, type ProblemDetail } from './problem'

export const CORRELATION_HEADER = 'X-Correlation-Id'

function newCorrelationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/**
 * Custom fetch mutator used by all orval-generated hooks.
 * Adds the bearer token of the signed-in OIDC user and a correlation id,
 * parses JSON responses and maps error responses (RFC 9457) to {@link ApiError}.
 */
export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json, application/problem+json')
  if (!headers.has(CORRELATION_HEADER)) headers.set(CORRELATION_HEADER, newCorrelationId())
  const token = await getAccessToken()
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(url, { ...options, headers })
  const contentType = response.headers.get('content-type') ?? ''
  const isJson = contentType.includes('json')

  if (!response.ok) {
    let problem: ProblemDetail | undefined
    if (isJson) {
      try {
        problem = (await response.json()) as ProblemDetail
      } catch {
        problem = undefined
      }
    }
    throw new ApiError(
      response.status,
      problem,
      response.headers.get(CORRELATION_HEADER) ?? headers.get(CORRELATION_HEADER) ?? undefined,
    )
  }

  if (response.status === 204 || !isJson) {
    return undefined as T
  }
  return (await response.json()) as T
}

export default apiFetch

/** Picked up by orval: the error type of all generated hooks. */
export type ErrorType<_Error> = ApiError
