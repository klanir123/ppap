import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { invalidateResource } from '@/features/masterdata/api/resource'

import * as api from './client'
import type { CaseListParams, CreateCaseInput, DecisionInput, ManualCaseInput } from './types'

/** Query keys start with the URL (same convention as the orval keys) → prefix invalidation. */
export const approvalKeys = {
  list: (params: CaseListParams) => [api.CASES_URL, params] as const,
  detail: (id: string) => [`${api.CASES_URL}/${id}`] as const,
  stats: () => [`${api.CASES_URL}/stats`] as const,
  submissions: (id: string) => [`${api.CASES_URL}/${id}/submissions`] as const,
  submission: (id: string, revision: number | 'draft') => [`${api.CASES_URL}/${id}/submissions/${revision}`] as const,
  formTemplates: () => [`${api.API_BASE}/form-templates`] as const,
}

/** Every write touches the case, its lists, stats and audit trail. */
export function invalidateApprovals(queryClient: QueryClient) {
  return invalidateResource(queryClient, api.CASES_URL)
}

export function useCasePage(params: CaseListParams, enabled = true) {
  return useQuery({
    queryKey: approvalKeys.list(params),
    queryFn: ({ signal }) => api.listCases(params, { signal }),
    placeholderData: keepPreviousData,
    enabled,
  })
}

export function useCase(id: string | undefined) {
  return useQuery({
    queryKey: approvalKeys.detail(id ?? ''),
    queryFn: ({ signal }) => api.getCase(id!, { signal }),
    enabled: Boolean(id),
  })
}

export function useCaseStats(enabled = true) {
  return useQuery({
    queryKey: approvalKeys.stats(),
    queryFn: ({ signal }) => api.getCaseStats({ signal }),
    enabled,
  })
}

export function useSubmission(id: string, revision: number | 'draft' | undefined) {
  return useQuery({
    queryKey: approvalKeys.submission(id, revision ?? 'draft'),
    queryFn: ({ signal }) => api.getSubmission(id, revision!, { signal }),
    enabled: revision !== undefined,
  })
}

export function useFormTemplates() {
  return useQuery({
    queryKey: approvalKeys.formTemplates(),
    queryFn: ({ signal }) => api.listFormTemplates({ signal }),
    staleTime: 5 * 60_000,
  })
}

function useInvalidatingMutation<TVars, TResult>(mutationFn: (vars: TVars) => Promise<TResult>, silent = true) {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { silent },
    mutationFn,
    // not awaited on purpose: per-call callbacks (navigate, toast, close) must run before the refetch
    // re-renders the page – a case that changes status can unmount the component that started the mutation
    onSuccess: () => {
      void invalidateApprovals(queryClient)
    },
  })
}

export function useCreateCase() {
  return useInvalidatingMutation((input: CreateCaseInput) => api.createCase(input))
}

export function useCreateManualCase() {
  return useInvalidatingMutation((input: ManualCaseInput) => api.createManualCase(input))
}

export function useTransition() {
  return useInvalidatingMutation(
    ({ id, action, version }: { id: string; action: api.TransitionAction; version?: number }) =>
      api.transitionCase(id, action, version),
  )
}

export function useCancelCase() {
  return useInvalidatingMutation(({ id, reason, version }: { id: string; reason: string; version?: number }) =>
    api.cancelCase(id, reason, version),
  )
}

export function useNewRevision() {
  return useInvalidatingMutation(({ id, version }: { id: string; version?: number }) => api.newRevision(id, version))
}

export function useSubmitDraft() {
  return useInvalidatingMutation(({ id, version }: { id: string; version?: number }) => api.submitDraft(id, version))
}

export function useDecision() {
  return useInvalidatingMutation(({ id, input, version }: { id: string; input: DecisionInput; version?: number }) =>
    api.postDecision(id, input, version),
  )
}

export function useUploadDocument() {
  return useMutation({ meta: { silent: true }, mutationFn: (file: File) => api.uploadDocument(file) })
}
