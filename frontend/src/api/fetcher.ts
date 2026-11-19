import { getAccessToken } from './auth-token'
import { ApiError, type ProblemDetail } from './problem'

export const CORRELATION_HEADER = 'X-Correlation-Id'

function newCorrelationId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** Extra, non-standard request option: inspect the raw response (e.g. read the `ETag` of a write). */
export type ApiRequestInit = RequestInit & { onResponse?: (response: Response) => void }

/**
 * Custom fetch mutator used by all orval-generated hooks.
 * Adds the bearer token of the signed-in OIDC user and a correlation id,
 * parses JSON responses and maps error responses (RFC 9457) to {@link ApiError}.
 */
export async function apiFetch<T>(url: string, options: ApiRequestInit = {}): Promise<T> {
  const { onResponse, ...init } = options
  const response = await apiFetchResponse(url, init)
  onResponse?.(response)
  const contentType = response.headers.get('content-type') ?? ''
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T
  }
  if (contentType.includes('json')) return (await response.json()) as T
  // binary/text downloads (e.g. text/csv error report) – typed as Blob by orval
  if (contentType) return (await response.blob()) as T
  return undefined as T
}

/** Downloads a binary/text response (e.g. CSV error report) with the same auth and error handling. */
export async function apiFetchBlob(url: string, options: RequestInit = {}): Promise<Blob> {
  const response = await apiFetchResponse(url, { ...options, headers: { Accept: '*/*', ...options.headers } })
  return response.blob()
}

/** Low-level request: auth + correlation id; throws {@link ApiError} for non-2xx responses. */
export async function apiFetchResponse(url: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  if (!headers.has('Accept')) headers.set('Accept', 'application/json, application/problem+json')
  if (!headers.has(CORRELATION_HEADER)) headers.set(CORRELATION_HEADER, newCorrelationId())
  const token = await getAccessToken()
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(url, { ...options, headers })
  const isJson = (response.headers.get('content-type') ?? '').includes('json')

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
  return response
}

export default apiFetch

/** Picked up by orval: the error type of all generated hooks. */
export type ErrorType<_Error> = ApiError
