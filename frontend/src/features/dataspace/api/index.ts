/**
 * Dataspace view (E5-06, docs/api/cascade-transparency-api.md §6) over the orval-generated client.
 */
import { useQuery } from '@tanstack/react-query'

import type { ApiRequestInit } from '@/api/fetcher'
import {
  getDataspaceTopology,
  getGetDataspaceTopologyUrl,
  getListRecentExchangeUrl,
  listRecentExchange,
} from '@/api/generated/endpoints/dataspace/dataspace'
import type {
  PartnerNodeDto,
  RecentMessageDto,
  RecentStepDto,
  RelationEdgeDto,
  TopologyDto,
} from '@/api/generated/model'

export type PartnerRelationRole = 'CUSTOMER' | 'SUPPLIER'
export type RelationEdge = RelationEdgeDto
/** Partner connector; `relation` = primary relation (customer first), `relations` = all. */
export type TopologyPartner = PartnerNodeDto
export type Topology = TopologyDto
export type DataspaceStep = RecentStepDto
/**
 * Lean exchange record for all roles: `id` = message id (same as `messageId` in `exchange.changed` /
 * `connector.log` – used for de-duplication), `recordId` = technical id.
 */
export type DataspaceMessage = RecentMessageDto

type Options = ApiRequestInit | undefined

export const getTopology = (options?: Options) => getDataspaceTopology(options)

export const getRecentMessages = (limit: number, options?: Options) => listRecentExchange({ limit }, options)

export const dataspaceKeys = {
  topology: () => [getGetDataspaceTopologyUrl()] as const,
  recent: (limit: number) => [getListRecentExchangeUrl().split('?')[0]!, { limit }] as const,
}

export function useTopology() {
  return useQuery({ queryKey: dataspaceKeys.topology(), queryFn: ({ signal }) => getTopology({ signal }) })
}

export function useRecentMessages(limit = 50) {
  return useQuery({
    queryKey: dataspaceKeys.recent(limit),
    queryFn: ({ signal }) => getRecentMessages(limit, { signal }),
  })
}
