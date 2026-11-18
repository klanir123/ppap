import { z } from 'zod'

import { optionalText, requiredText, zBpnl, zBpns, zOptionalCountry } from '@/lib/validation'

import type { Site, SiteInput } from '../api/types'

export const siteSchema = z.object({
  bpns: zBpns,
  name: requiredText(255),
  ownerBpnl: zBpnl,
  street: optionalText(255),
  postalCode: optionalText(16),
  city: optionalText(128),
  country: zOptionalCountry,
})
export type SiteFormValues = z.input<typeof siteSchema>

export function siteDefaults(site: Site | undefined, ownBpnl: string): SiteFormValues {
  return {
    bpns: site?.bpns ?? '',
    name: site?.name ?? '',
    ownerBpnl: site?.ownerBpnl ?? ownBpnl,
    street: site?.street ?? '',
    postalCode: site?.postalCode ?? '',
    city: site?.city ?? '',
    country: site?.country ?? '',
  }
}

export function toSiteInput(values: z.output<typeof siteSchema>): SiteInput {
  return values
}
