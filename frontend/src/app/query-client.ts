import { MutationCache, QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { isApiError } from '@/api/problem'
import i18n from '@/lib/i18n'

declare module '@tanstack/react-query' {
  interface Register {
    mutationMeta: { silent?: boolean }
  }
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        // client errors (4xx) are not retried
        retry: (count, error) => !(isApiError(error) && error.status < 500) && count < 2,
      },
    },
    mutationCache: new MutationCache({
      onError: (error, _variables, _context, mutation) => {
        // forms map problem details onto fields themselves
        if (mutation.meta?.silent) return
        toast.error(isApiError(error) ? (error.problem?.title ?? error.message) : i18n.t('errors:generic.title'), {
          description: isApiError(error) ? error.problem?.detail : undefined,
        })
      },
    }),
  })
}
