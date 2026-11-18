import { z } from 'zod'

import { optionalText, zBpnl, zOptionalBpns, zPartNumber } from '@/lib/validation'

import type { PartMapping, PartMappingInput, Relation, RelationInput } from '../api/types'

export const relationSchema = z.object({
  partnerBpnl: zBpnl,
  partnerRole: z.enum(['CUSTOMER', 'SUPPLIER'], 'REQUIRED'),
  exchangeMode: z.enum(['PUSH', 'PULL', 'PUSH_NOTIFY_PULL_PAYLOAD'], 'REQUIRED'),
  policyProfile: optionalText(64),
})
export type RelationFormValues = z.input<typeof relationSchema>

export function relationDefaults(relation?: Relation): RelationFormValues {
  return {
    partnerBpnl: relation?.partnerBpnl ?? '',
    partnerRole: relation?.partnerRole ?? ('' as RelationFormValues['partnerRole']),
    exchangeMode: relation?.exchangeMode ?? 'PUSH_NOTIFY_PULL_PAYLOAD',
    policyProfile: relation?.policyProfile ?? '',
  }
}

export function toRelationInput(values: z.output<typeof relationSchema>): RelationInput {
  return values
}

export const partMappingSchema = z.object({
  partNumber: zPartNumber,
  partnerPartNumber: zPartNumber,
  ownSiteBpns: zOptionalBpns,
  partnerSiteBpns: zOptionalBpns,
})
export type PartMappingFormValues = z.input<typeof partMappingSchema>

export function partMappingDefaults(mapping?: PartMapping): PartMappingFormValues {
  return {
    partNumber: mapping?.partNumber ?? '',
    partnerPartNumber: mapping?.partnerPartNumber ?? '',
    ownSiteBpns: mapping?.ownSiteBpns ?? '',
    partnerSiteBpns: mapping?.partnerSiteBpns ?? '',
  }
}

/** On create the part is referenced by number; on update by id (part cannot change). */
export function toPartMappingInput(
  values: z.output<typeof partMappingSchema>,
  existing?: PartMapping,
): PartMappingInput {
  const { partNumber, ...rest } = values
  return existing ? { ...rest, partId: existing.partId } : { ...rest, partNumber }
}
