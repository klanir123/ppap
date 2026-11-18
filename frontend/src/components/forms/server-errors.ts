import type { FieldError, FieldValues, Path, UseFormSetError } from 'react-hook-form'

import { mapProblemToFields, specificCode, translateErrorCode, type MappedProblem } from '@/lib/problem-errors'

/** Server errors are stored as `type: "server:<CODE>"`, `message: <server message>`. */
const SERVER_PREFIX = 'server:'

export function applyProblemToForm<T extends FieldValues>(
  error: unknown,
  fields: readonly string[],
  setError: UseFormSetError<T>,
): MappedProblem {
  const mapped = mapProblemToFields(error, fields)
  mapped.fieldErrors.forEach((fe, index) => {
    setError(
      fe.field as Path<T>,
      { type: `${SERVER_PREFIX}${specificCode(fe.code, fe.field)}`, message: fe.message ?? '' },
      { shouldFocus: index === 0 },
    )
  })
  return mapped
}

/** Client (zod) errors carry the code as message; server errors carry it in `type`. */
export function fieldErrorText(error: Pick<FieldError, 'type' | 'message'> | undefined): string | undefined {
  if (!error) return undefined
  const type = typeof error.type === 'string' ? error.type : ''
  if (type.startsWith(SERVER_PREFIX)) return translateErrorCode(type.slice(SERVER_PREFIX.length), error.message)
  return translateErrorCode(error.message || 'INVALID_VALUE', error.message)
}
