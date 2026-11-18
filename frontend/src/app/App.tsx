import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { RouterProvider } from '@tanstack/react-router'
import { ThemeProvider } from 'next-themes'
import { useState } from 'react'

import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { AuthGate } from '@/features/auth/AuthGate'
import { AuthProvider } from '@/features/auth/AuthProvider'
import { ConfigProvider } from '@/features/config/ConfigProvider'

import { createQueryClient } from './query-client'
import { createAppRouter } from './router'

/**
 * Bootstrap order: runtime config → OIDC → current user → router.
 * The QueryClient sits outermost because the config itself is fetched through it.
 */
export function App({ queryClient: injected }: { queryClient?: QueryClient }) {
  const [queryClient] = useState(() => injected ?? createQueryClient())
  const [router] = useState(() => createAppRouter({ queryClient }))

  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      storageKey="ppap.theme"
      disableTransitionOnChange
    >
      <QueryClientProvider client={queryClient}>
        <TooltipProvider delayDuration={300}>
          <ConfigProvider>
            <AuthProvider>
              <AuthGate>
                <RouterProvider router={router} />
              </AuthGate>
            </AuthProvider>
          </ConfigProvider>
          <Toaster position="bottom-right" closeButton />
        </TooltipProvider>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
