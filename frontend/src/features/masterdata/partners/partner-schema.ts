import { z } from 'zod'

import { requiredText, zBpnl, zOptionalDid, zOptionalEmail, zOptionalUrl } from '@/lib/validation'

import type { Partner, PartnerInput } from '../api/types'

/** The own tenant must not be a partner (SELF_REFERENCE) – checked client-side as well. */
export const createPartnerSchema = (ownBpnl: string) =>
  z.object({
    bpnl: zBpnl.refine((v) => v !== ownBpnl, 'SELF_REFERENCE'),
    name: requiredText(255),
    roles: z.array(z.enum(['CUSTOMER', 'SUPPLIER'])).min(1, 'MIN_ONE'),
    dspEndpoint: zOptionalUrl,
    did: zOptionalDid,
    contactEmail: zOptionalEmail,
  })
type PartnerSchema = ReturnType<typeof createPartnerSchema>
export type PartnerFormValues = z.input<PartnerSchema>

export function partnerDefaults(partner?: Partner): PartnerFormValues {
  return {
    bpnl: partner?.bpnl ?? '',
    name: partner?.name ?? '',
    roles: partner?.roles ?? [],
    dspEndpoint: partner?.dspEndpoint ?? '',
    did: partner?.did ?? '',
    contactEmail: partner?.contactEmail ?? '',
  }
}

export function toPartnerInput(values: z.output<PartnerSchema>): PartnerInput {
  return values
}
