/**
 * Master data lookups used by the approval wizards (generated master data client).
 */
import { useQuery } from '@tanstack/react-query'

import { getSearchPartMappingsQueryKey, searchPartMappings } from '@/api/generated/endpoints/relations/relations'
import { elementsApi, levelsApi, reasonsApi } from '@/features/masterdata/api'

import type { CaseRole } from './types'

export interface PartnerOption {
  relationId: string
  partnerBpnl: string
  partnerName?: string
  partnerPartNumber: string
  exchangeReady: boolean
}

export interface SupplierPartOption {
  partId: string
  partNumber: string
  partNameDe?: string
  partners: PartnerOption[]
}

const MAX = 200

/** Groups part mappings of all active relations with the given partner role by own part. */
export function groupPartOptions(
  mappings: { partId: string; partNumber?: string; partNameDe?: string; option: PartnerOption }[],
): SupplierPartOption[] {
  const byPart = new Map<string, SupplierPartOption>()
  for (const m of mappings) {
    const existing = byPart.get(m.partId)
    if (existing) existing.partners.push(m.option)
    else
      byPart.set(m.partId, {
        partId: m.partId,
        partNumber: m.partNumber ?? m.partId,
        partNameDe: m.partNameDe,
        partners: [m.option],
      })
  }
  return [...byPart.values()].sort((a, b) => a.partNumber.localeCompare(b.partNumber, undefined, { numeric: true }))
}

/** Own parts that have a mapping to a partner in the given role (SUPPLIER for requests, CUSTOMER for manual capture). */
export function useSupplierPartOptions(partnerRole: CaseRole, q: string | undefined, enabled = true) {
  const params = { partnerRole, q: q || undefined, size: MAX }
  return useQuery({
    queryKey: getSearchPartMappingsQueryKey(params),
    enabled,
    queryFn: async ({ signal }) => {
      const page = await searchPartMappings(params, { signal })
      return groupPartOptions(
        page.content.map((m) => ({
          partId: m.part.id,
          partNumber: m.part.partNumber,
          partNameDe: m.part.nameDe,
          option: {
            relationId: m.relationId,
            partnerBpnl: m.partner.bpnl,
            partnerName: m.partner.name,
            partnerPartNumber: m.partnerPartNumber,
            exchangeReady: m.partner.exchangeReady,
          },
        })),
      )
    },
  })
}

/** Active reasons, levels (with matrix) and elements. */
export function useCatalogue() {
  const reasons = reasonsApi.useAll({ active: true })
  const levels = levelsApi.useAll({ active: true })
  const elements = elementsApi.useAll({ active: true })
  return {
    reasons: reasons.data ?? [],
    levels: levels.data ?? [],
    elements: elements.data ?? [],
    isPending: reasons.isPending || levels.isPending || elements.isPending,
    isError: reasons.isError || levels.isError || elements.isError,
  }
}
