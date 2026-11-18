/**
 * Format rules shared with the backend (docs/api/masterdata-api.md › Validierungsregeln).
 * Zod messages are error *codes*; they are translated via `errors:codes.<CODE>`.
 */
import { z } from 'zod'

export const PATTERNS = {
  bpnl: /^BPNL[0-9A-Z]{12}$/,
  bpns: /^BPNS[0-9A-Z]{12}$/,
  partNumber: /^[A-Za-z0-9._\- /]+$/,
  did: /^did:[a-z0-9]+:.+$/,
  country: /^[A-Z]{2}$/,
  catalogCode: /^[A-Z0-9][A-Z0-9_-]{1,31}$/,
  catenaXId: /^urn:uuid:[0-9a-fA-F-]{36}$/,
} as const

/** Optional input field: '' (empty input) is accepted and becomes `undefined`. */
export function optional<T extends z.ZodType<string>>(schema: T) {
  return z
    .union([z.literal(''), schema])
    .optional()
    .transform((value) => (value === '' ? undefined : (value as z.output<T> | undefined)))
}

export const requiredText = (max = 255) => z.string().trim().min(1, 'REQUIRED').max(max, 'TOO_LONG')
export const optionalText = (max = 255) => optional(z.string().trim().max(max, 'TOO_LONG'))

export const zBpnl = z.string().trim().regex(PATTERNS.bpnl, 'INVALID_BPNL')
export const zBpns = z.string().trim().regex(PATTERNS.bpns, 'INVALID_BPNS')
export const zOptionalBpns = optional(zBpns)
export const zPartNumber = z
  .string()
  .trim()
  .min(1, 'REQUIRED')
  .max(64, 'TOO_LONG')
  .regex(PATTERNS.partNumber, 'INVALID_PART_NUMBER')
export const zOptionalPartNumber = optional(zPartNumber)
export const zCatalogCode = z.string().trim().regex(PATTERNS.catalogCode, 'INVALID_CODE')
export const zOptionalUrl = optional(
  z
    .string()
    .trim()
    .refine((v) => {
      try {
        const url = new URL(v)
        return url.protocol === 'http:' || url.protocol === 'https:'
      } catch {
        return false
      }
    }, 'INVALID_URL'),
)
export const zOptionalDid = optional(z.string().trim().regex(PATTERNS.did, 'INVALID_DID'))
export const zOptionalEmail = optional(z.email('INVALID_EMAIL'))
export const zOptionalCountry = optional(z.string().trim().toUpperCase().regex(PATTERNS.country, 'INVALID_COUNTRY'))
export const zOptionalCatenaXId = optional(z.string().trim().regex(PATTERNS.catenaXId, 'INVALID_CATENAX_ID'))
export const zSortOrder = z.number('INVALID_NUMBER').int('INVALID_NUMBER').min(0, 'INVALID_NUMBER')

/** JSON text: must parse to a JSON object (used for `valueSchema`). */
export function isJsonObjectText(value: string): boolean {
  try {
    const parsed: unknown = JSON.parse(value)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)
  } catch {
    return false
  }
}
export const zOptionalJsonObject = optional(z.string().refine(isJsonObjectText, 'INVALID_JSON'))
