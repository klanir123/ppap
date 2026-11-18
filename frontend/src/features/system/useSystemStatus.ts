import { useGetSystemStatus } from '@/api/generated/endpoints/system/system'

export const SYSTEM_STATUS_REFETCH_MS = 10_000

export function useSystemStatus() {
  return useGetSystemStatus({
    query: { refetchInterval: SYSTEM_STATUS_REFETCH_MS, refetchIntervalInBackground: false },
  })
}
