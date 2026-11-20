/**
 * Usage metering (UC-12, docs/api/cascade-transparency-api.md §5) over the orval-generated client.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { apiFetchBlob, type ApiRequestInit } from '@/api/fetcher'
import {
  getExportUsageCsvUrl,
  getGetUsageUrl,
  getUsage as genGetUsage,
  recomputeUsage as genRecomputeUsage,
} from '@/api/generated/endpoints/usage/usage'
import type { MonthUsageDto, RecomputationDto } from '@/api/generated/model'

export const USAGE_URL = getGetUsageUrl().split('?')[0]!

/** One month (`YYYY-MM`); `approvalCases` = submitted + received revision 1, `details` = raw counters. */
export type UsageMonth = MonthUsageDto

export interface UsageRange {
  from: string
  to: string
}

/** Result of a recompute: `consistent` = event counters equal the recomputation (AK-12-1). */
export type UsageRecomputation = RecomputationDto

type Options = ApiRequestInit | undefined

export const getUsage = (range: UsageRange, options?: Options) => genGetUsage(range, options)

/** CSV as raw bytes (the generated client would read it as text). */
export const exportUsageCsv = (range: UsageRange) =>
  apiFetchBlob(getExportUsageCsvUrl(range), { headers: { Accept: 'text/csv' } })

export const recomputeUsage = () => genRecomputeUsage()

export const usageKeys = { range: (range: UsageRange) => [USAGE_URL, range] as const }

export function useUsage(range: UsageRange) {
  return useQuery({
    queryKey: usageKeys.range(range),
    queryFn: ({ signal }) => getUsage(range, { signal }),
  })
}

export function useRecomputeUsage() {
  const queryClient = useQueryClient()
  return useMutation({
    meta: { silent: true },
    mutationFn: recomputeUsage,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [USAGE_URL] }),
  })
}
