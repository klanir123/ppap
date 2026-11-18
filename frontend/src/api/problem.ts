/** RFC 9457 problem details as returned by the backend. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [extension: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | undefined
  readonly correlationId: string | undefined

  constructor(status: number, problem: ProblemDetail | undefined, correlationId: string | undefined) {
    super(problem?.title ?? problem?.detail ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.correlationId = correlationId
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
