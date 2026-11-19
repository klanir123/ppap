import { toast } from 'sonner'

import { isApiError } from '@/api/problem'
import { translateErrorCode } from '@/lib/problem-errors'

/** Problem code of an API error (`VERSION_CONFLICT` for 412 without code). */
export function problemCode(error: unknown): string | undefined {
  if (!isApiError(error)) return undefined
  return error.problem?.code ?? (error.status === 412 ? 'VERSION_CONFLICT' : undefined)
}

/** Translated, user-facing message of an error (contract codes → errors:codes.*). */
export function problemMessage(error: unknown): string {
  if (!isApiError(error)) return translateErrorCode('UNEXPECTED', error instanceof Error ? error.message : undefined)
  return translateErrorCode(problemCode(error) ?? `HTTP_${error.status}`, error.problem?.detail ?? error.problem?.title)
}

export function toastProblem(error: unknown, title?: string) {
  const message = problemMessage(error)
  if (title) toast.error(title, { description: message })
  else toast.error(message)
}
