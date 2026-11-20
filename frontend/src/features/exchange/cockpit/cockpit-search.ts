import { z } from 'zod'

import { DEFAULT_PAGE_SIZE } from '@/components/data-table'

import { EXCHANGE_DIRECTIONS, EXCHANGE_STATUSES, MESSAGE_TYPES, type ExchangeMessageParams } from '../api/types'

export const COCKPIT_TABS = ['overview', 'offers', 'policies', 'catalog', 'processes', 'messages', 'log'] as const
export type CockpitTab = (typeof COCKPIT_TABS)[number]

/** `/cockpit` search params: tab, message filters, paging, opened message (drawer), catalog partner, process filters. */
export const cockpitSearchSchema = z.object({
  tab: z.enum(COCKPIT_TABS).optional().catch(undefined),
  direction: z.enum(EXCHANGE_DIRECTIONS).optional().catch(undefined),
  status: z.enum(EXCHANGE_STATUSES).optional().catch(undefined),
  /** Record kind (`messageType`), e.g. PULL_SUBMISSION for the customer's pulls. */
  type: z.enum(MESSAGE_TYPES).optional().catch(undefined),
  partner: z.string().optional().catch(undefined),
  page: z.number().int().min(0).optional().catch(undefined),
  size: z.number().int().min(1).max(200).optional().catch(undefined),
  message: z.string().optional().catch(undefined),
  /** Partner catalog tab: selected partner (BPNL). */
  catalogPartner: z.string().optional().catch(undefined),
  /** Negotiations / transfers tab: state filters. */
  negotiationState: z.string().max(64).optional().catch(undefined),
  transferState: z.string().max(64).optional().catch(undefined),
  /** Asset / policy detail (id). */
  asset: z.string().optional().catch(undefined),
})
export type CockpitSearch = z.infer<typeof cockpitSearchSchema>

export function toMessageParams(search: CockpitSearch): ExchangeMessageParams {
  return {
    direction: search.direction,
    status: search.status,
    messageType: search.type ? [search.type] : undefined,
    partnerBpnl: search.partner,
    page: search.page ?? 0,
    size: search.size ?? DEFAULT_PAGE_SIZE,
  }
}

export function hasMessageFilters(search: CockpitSearch): boolean {
  return Boolean(search.direction || search.status || search.type || search.partner)
}

/** Active tab; links from earlier versions (message filters / opened message, no tab) land on the messages. */
export function activeTab(search: CockpitSearch): CockpitTab {
  if (search.tab) return search.tab
  return hasMessageFilters(search) || search.message ? 'messages' : 'overview'
}
