import { z } from 'zod'

import { DEFAULT_PAGE_SIZE } from '@/components/data-table'

import { EXCHANGE_DIRECTIONS, EXCHANGE_STATUSES, type ExchangeMessageParams } from '../api/types'

/** `/cockpit` search params: message filters, paging and the opened message (drawer). */
export const cockpitSearchSchema = z.object({
  direction: z.enum(EXCHANGE_DIRECTIONS).optional().catch(undefined),
  status: z.enum(EXCHANGE_STATUSES).optional().catch(undefined),
  partner: z.string().optional().catch(undefined),
  page: z.number().int().min(0).optional().catch(undefined),
  size: z.number().int().min(1).max(200).optional().catch(undefined),
  message: z.string().optional().catch(undefined),
})
export type CockpitSearch = z.infer<typeof cockpitSearchSchema>

export function toMessageParams(search: CockpitSearch): ExchangeMessageParams {
  return {
    direction: search.direction,
    status: search.status,
    partnerBpnl: search.partner,
    page: search.page ?? 0,
    size: search.size ?? DEFAULT_PAGE_SIZE,
  }
}

export function hasMessageFilters(search: CockpitSearch): boolean {
  return Boolean(search.direction || search.status || search.partner)
}
