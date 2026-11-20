import { z } from 'zod'

import { optionalText, requiredText } from '@/lib/validation'

import type { PolicyProfile, PolicyProfileInput } from '../api/types'

/** Catena-X usage purpose, e.g. `cx.quality.base:1` (dot-separated lower-case segments + `:` version). */
export const USAGE_PURPOSE_PATTERN = /^[a-z][a-z0-9-]*(\.[a-z0-9-]+)+:\d+(\.\d+)*$/
/** Framework agreement, e.g. `DataExchangeGovernance:1.0`. */
export const FRAMEWORK_AGREEMENT_PATTERN = /^[A-Za-z][A-Za-z0-9.-]*(:\d+(\.\d+)*)?$/
export const PROFILE_CODE_PATTERN = /^[a-z0-9][a-z0-9_-]{1,63}$/

/** Single usage purpose → error code or undefined (chips input validates before adding). */
export function usagePurposeError(value: string, existing: readonly string[] = []): string | undefined {
  const v = value.trim()
  if (!v) return 'REQUIRED'
  if (!USAGE_PURPOSE_PATTERN.test(v)) return 'INVALID_USAGE_PURPOSE'
  if (existing.includes(v)) return 'DUPLICATE_USAGE_PURPOSE'
  return undefined
}

export const policyProfileSchema = z.object({
  code: z.string().trim().regex(PROFILE_CODE_PATTERN, 'INVALID_PROFILE_CODE'),
  name: requiredText(120),
  description: optionalText(500),
  accessType: z.enum(['BPN_GROUP', 'BPN'], 'REQUIRED'),
  membershipRequired: z.boolean(),
  frameworkAgreement: z
    .string()
    .trim()
    .min(1, 'FRAMEWORK_AGREEMENT_REQUIRED')
    .regex(FRAMEWORK_AGREEMENT_PATTERN, 'INVALID_FRAMEWORK_AGREEMENT'),
  usagePurposes: z
    .array(z.string().trim().regex(USAGE_PURPOSE_PATTERN, 'INVALID_USAGE_PURPOSE'))
    .min(1, 'MIN_ONE_USAGE_PURPOSE')
    .refine((list) => new Set(list).size === list.length, 'DUPLICATE_USAGE_PURPOSE'),
  active: z.boolean(),
})
export type PolicyProfileFormValues = z.input<typeof policyProfileSchema>

export function policyProfileDefaults(profile?: PolicyProfile): PolicyProfileFormValues {
  return {
    code: profile?.code ?? '',
    name: profile?.name ?? '',
    description: profile?.description ?? '',
    accessType: profile?.access.type ?? 'BPN_GROUP',
    membershipRequired: profile?.access.membershipRequired ?? true,
    frameworkAgreement: profile?.usage.frameworkAgreement ?? 'DataExchangeGovernance:1.0',
    usagePurposes: profile?.usage.usagePurposes ?? [],
    active: profile?.active ?? true,
  }
}

/** Form → API; additional constraints are not editable in the UI and are kept as they are. */
export function toPolicyProfileInput(
  values: z.output<typeof policyProfileSchema>,
  existing?: PolicyProfile,
): PolicyProfileInput {
  return {
    code: values.code,
    name: values.name,
    description: values.description,
    access: { type: values.accessType, membershipRequired: values.membershipRequired },
    usage: {
      frameworkAgreement: values.frameworkAgreement,
      usagePurposes: values.usagePurposes,
      additionalConstraints: existing?.usage.additionalConstraints ?? [],
    },
    active: values.active,
  }
}
