import { Outlet } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { LiveEventsProvider } from '@/features/exchange/live/LiveEventsProvider'

import { AppSidebar } from './AppSidebar'
import { TopBar } from './TopBar'

export function AppShell() {
  const { t } = useTranslation()
  return (
    <LiveEventsProvider>
      <SidebarProvider>
        <a
          href="#main-content"
          className="sr-only z-50 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
        >
          {t('skipToContent')}
        </a>
        <AppSidebar />
        <SidebarInset className="min-w-0">
          <TopBar />
          <main id="main-content" tabIndex={-1} className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8 lg:py-8">
            <div className="mx-auto w-full max-w-7xl">
              <Outlet />
            </div>
          </main>
        </SidebarInset>
      </SidebarProvider>
    </LiveEventsProvider>
  )
}
