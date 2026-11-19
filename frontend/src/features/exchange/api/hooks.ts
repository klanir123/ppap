import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { isApiError } from '@/api/problem'
import { invalidateApprovals, useCaseStats } from '@/features/approvals/api'

import { useLiveConnected } from '../live/live-context'
import { isInFlight } from '../model/status'
import { normalizeTwin } from '../model/twin'
import * as api from './client'
import type { AssignPartInput, ExchangeMessageParams, ExchangeRecord } from './types'

/** Keys start with the URL (orval convention) → prefix invalidation; case keys sit below the case URL. */
export const exchangeKeys = {
  case: (caseId: string) => [api.caseExchangeUrl(caseId)] as const,
  messages: (params: ExchangeMessageParams) => [api.MESSAGES_URL, params] as const,
  message: (id: string) => [`${api.MESSAGES_URL}/${id}`] as const,
  overview: () => [`${api.EXCHANGE_URL}/status`] as const,
  twin: (exchangeId: string) => [api.twinUrl(exchangeId)] as const,
}

/** Fallback polling while a message is still on its way and the live connection is down. */
const FALLBACK_POLL_MS = 5_000

function pollWhileInFlight(records: ExchangeRecord[] | undefined, live: boolean) {
  return !live && records?.some((r) => isInFlight(r.status)) ? FALLBACK_POLL_MS : false
}

export function useCaseExchange(caseId: string | undefined, enabled = true) {
  const live = useLiveConnected()
  return useQuery({
    queryKey: exchangeKeys.case(caseId ?? ''),
    queryFn: ({ signal }) => api.listCaseExchange(caseId!, { signal }),
    enabled: Boolean(caseId) && enabled,
    refetchInterval: (query) => pollWhileInFlight(query.state.data, live),
  })
}

export function useExchangeMessages(params: ExchangeMessageParams) {
  const live = useLiveConnected()
  return useQuery({
    queryKey: exchangeKeys.messages(params),
    queryFn: ({ signal }) => api.listMessages(params, { signal }),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => pollWhileInFlight(query.state.data?.content, live),
  })
}

export function useExchangeMessage(id: string | undefined) {
  return useQuery({
    queryKey: exchangeKeys.message(id ?? ''),
    queryFn: ({ signal }) => api.getMessage(id!, { signal }),
    enabled: Boolean(id),
  })
}

export function useExchangeOverview() {
  return useQuery({
    queryKey: exchangeKeys.overview(),
    queryFn: ({ signal }) => api.getExchangeOverview({ signal }),
    refetchInterval: 30_000,
  })
}

/** Digital twin of a case; `null` while none is registered (404). */
export function useTwin(exchangeId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: exchangeKeys.twin(exchangeId ?? ''),
    queryFn: async ({ signal }) => {
      try {
        return normalizeTwin(await api.getTwin(exchangeId!, { signal }))
      } catch (error) {
        if (isApiError(error) && error.status === 404) return null
        throw error
      }
    },
    enabled: Boolean(exchangeId) && enabled,
  })
}

/** Number of cases in the clarification list (UC-04 A1) – from the case stats. */
export function useClarificationCount(enabled = true) {
  const stats = useCaseStats(enabled)
  return { ...stats, data: stats.data?.needsClarification }
}

export function invalidateExchange(queryClient: QueryClient) {
  return queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey[0]
      return (
        typeof key === 'string' &&
        (key.startsWith(api.EXCHANGE_URL) || key.startsWith(api.TWINS_URL) || /\/exchange$/.test(key))
      )
    },
  })
}

export function useRetryMessage() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.retryMessage(id),
    onSuccess: () => {
      void invalidateExchange(queryClient)
    },
  })
}

export function useAssignPart() {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { silent: true },
    mutationFn: ({ caseId, input, version }: { caseId: string; input: AssignPartInput; version?: number }) =>
      api.assignPart(caseId, input, version),
    onSuccess: () => {
      void invalidateApprovals(queryClient)
    },
  })
}
