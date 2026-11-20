import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'

import * as api from './connector'

/** Keys start with the URL (prefix invalidation via `exchange.changed`). */
export const connectorKeys = {
  overview: () => [`${api.CONNECTOR_URL}/overview`] as const,
  assets: () => [`${api.CONNECTOR_URL}/assets`] as const,
  policies: () => [`${api.CONNECTOR_URL}/policies`] as const,
  contractDefinitions: () => [`${api.CONNECTOR_URL}/contract-definitions`] as const,
  negotiations: (params: api.ProcessParams) => [`${api.CONNECTOR_URL}/negotiations`, params] as const,
  transfers: (params: api.ProcessParams) => [`${api.CONNECTOR_URL}/transfers`, params] as const,
}

export function useConnectorOverview() {
  return useQuery({
    queryKey: connectorKeys.overview(),
    queryFn: ({ signal }) => api.getConnectorOverview({ signal }),
    refetchInterval: 30_000,
  })
}

export function useConnectorAssets(enabled = true) {
  return useQuery({
    queryKey: connectorKeys.assets(),
    queryFn: ({ signal }) => api.listConnectorAssets({ signal }),
    enabled,
  })
}

export function useConnectorPolicies(enabled = true) {
  return useQuery({
    queryKey: connectorKeys.policies(),
    queryFn: ({ signal }) => api.listConnectorPolicies({ signal }),
    enabled,
  })
}

export function useContractDefinitions(enabled = true) {
  return useQuery({
    queryKey: connectorKeys.contractDefinitions(),
    queryFn: ({ signal }) => api.listContractDefinitions({ signal }),
    enabled,
  })
}

export function useNegotiations(params: api.ProcessParams, enabled = true) {
  return useQuery({
    queryKey: connectorKeys.negotiations(params),
    queryFn: ({ signal }) => api.listNegotiations(params, { signal }),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useTransfers(params: api.ProcessParams, enabled = true) {
  return useQuery({
    queryKey: connectorKeys.transfers(params),
    queryFn: ({ signal }) => api.listTransfers(params, { signal }),
    placeholderData: keepPreviousData,
    enabled,
  })
}

/** Live catalog request (DSP) – a mutation: every click fetches again, nothing is cached. */
export function usePartnerCatalog() {
  return useMutation({
    meta: { silent: true },
    mutationFn: async (partnerBpnl: string) => {
      const started = performance.now()
      const result = await api.fetchPartnerCatalog(partnerBpnl)
      return { ...result, durationMs: result.durationMs ?? Math.round(performance.now() - started) }
    },
  })
}
