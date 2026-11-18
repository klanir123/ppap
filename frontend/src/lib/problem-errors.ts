import { isApiError } from '@/api/problem'
import i18n from '@/lib/i18n'

export interface MappedFieldError {
  field: string
  /** Error code (server `code` or client zod message). */
  code: string
  /** Server message (English) – fallback if the code is not translated. */
  message?: string
}

export interface MappedProblem {
  fieldErrors: MappedFieldError[]
  /** Problem-level code for errors that could not be assigned to a field. */
  formCode?: string
  formMessage?: string
  /** Field errors whose field is not part of the form. */
  unmapped: MappedFieldError[]
  isVersionConflict: boolean
}

/** `roles[0]` / `address.city` → `roles` / `address` (first segment). */
export function rootField(field: string): string {
  return field.split(/[.[]/)[0] ?? field
}

/**
 * Maps an RFC 9457 problem (`errors: [{field, code, message}]`) onto form fields.
 * Fields are matched exactly first, then by their root segment.
 */
export function mapProblemToFields(error: unknown, formFields: readonly string[]): MappedProblem {
  const result: MappedProblem = { fieldErrors: [], unmapped: [], isVersionConflict: false }
  if (!isApiError(error)) {
    result.formCode = 'UNEXPECTED'
    result.formMessage = error instanceof Error ? error.message : undefined
    return result
  }
  const problem = error.problem
  result.isVersionConflict = problem?.code === 'VERSION_CONFLICT' || error.status === 412
  const known = new Set(formFields)
  const seen = new Set<string>()

  for (const fe of problem?.errors ?? []) {
    const code = fe.code ?? problem?.code ?? 'VALIDATION_FAILED'
    const entry = { code, message: fe.message }
    const target =
      fe.field && (known.has(fe.field) ? fe.field : known.has(rootField(fe.field)) ? rootField(fe.field) : undefined)
    if (target && !seen.has(target)) {
      seen.add(target)
      result.fieldErrors.push({ field: target, ...entry })
    } else if (!target) {
      result.unmapped.push({ field: fe.field ?? '', ...entry })
    }
  }

  if (result.fieldErrors.length === 0 || result.unmapped.length > 0 || result.isVersionConflict) {
    result.formCode = result.isVersionConflict ? 'VERSION_CONFLICT' : (problem?.code ?? `HTTP_${error.status}`)
    result.formMessage = problem?.detail ?? problem?.title
  }
  return result
}

/**
 * Translates an error code (`errors:codes.<CODE>`), falling back to the server message.
 * Components call this during render, so the active language is picked up automatically.
 */
export function translateErrorCode(code: string | undefined, fallback?: string): string {
  const unexpected = i18n.t('codes.UNEXPECTED', { ns: 'errors' })
  if (!code) return fallback ?? unexpected
  return i18n.t(`codes.${code}` as 'codes.UNEXPECTED', { ns: 'errors', defaultValue: fallback ?? unexpected })
}

/**
 * The backend reports generic format violations (`INVALID_FORMAT`); for known fields the UI shows
 * the specific rule (e.g. BPNL pattern) so users see what is expected (AK-01-4).
 */
const FORMAT_CODE_BY_FIELD: Record<string, string> = {
  bpnl: 'INVALID_BPNL',
  ownerBpnl: 'INVALID_BPNL',
  partnerBpnl: 'INVALID_BPNL',
  bpns: 'INVALID_BPNS',
  ownSiteBpns: 'INVALID_BPNS',
  partnerSiteBpns: 'INVALID_BPNS',
  partNumber: 'INVALID_PART_NUMBER',
  partnerPartNumber: 'INVALID_PART_NUMBER',
  drawingNumber: 'INVALID_PART_NUMBER',
  dspEndpoint: 'INVALID_URL',
  did: 'INVALID_DID',
  contactEmail: 'INVALID_EMAIL',
  country: 'INVALID_COUNTRY',
  code: 'INVALID_CODE',
  catenaXId: 'INVALID_CATENAX_ID',
  valueSchema: 'INVALID_SCHEMA',
}

export function specificCode(code: string | undefined, field: string | undefined): string | undefined {
  if (code === 'INVALID_FORMAT' && field) return FORMAT_CODE_BY_FIELD[rootField(field)] ?? code
  return code
}

/** Field-level message: specific format rule where known, otherwise the translated code. */
export function translateFieldError(code: string | undefined, field: string | undefined, fallback?: string): string {
  return translateErrorCode(specificCode(code, field), fallback)
}
