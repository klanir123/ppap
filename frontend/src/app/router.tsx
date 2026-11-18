import type { QueryClient } from '@tanstack/react-query'
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
  type RouterHistory,
} from '@tanstack/react-router'
import { Boxes, Cable, FileCheck2, GitFork, ListChecks, MapPin, Package, Users, type LucideIcon } from 'lucide-react'

import { AppShell } from '@/features/shell/AppShell'
import { RequirePermission } from '@/features/auth/RequirePermission'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { ComingSoonPage } from '@/features/placeholder/ComingSoonPage'
import { SystemStatusPage } from '@/features/system/SystemStatusPage'

import type { NavLabelKey } from './navigation'

export interface RouterContext {
  queryClient: QueryClient
}

declare module '@tanstack/react-router' {
  interface StaticDataRouteOption {
    /** Breadcrumb label (key in the `nav` namespace). */
    crumb?: NavLabelKey
  }
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: AppShell,
  notFoundComponent: NotFoundPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  staticData: { crumb: 'dashboard' },
  component: DashboardPage,
})

const approvalsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/approvals',
  staticData: { crumb: 'approvals' },
  component: () => (
    <RequirePermission permission="approvals:view">
      <ComingSoonPage titleKey="approvals" descriptionKey="approvals.description" icon={FileCheck2} phase={3} />
    </RequirePermission>
  ),
})

const masterdataRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/masterdata',
  staticData: { crumb: 'masterdata' },
})

const masterdataIndexRoute = createRoute({
  getParentRoute: () => masterdataRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: '/masterdata/parts' })
  },
})

function masterdataPage<const TPath extends string>(
  path: TPath,
  crumb: 'parts' | 'partners' | 'sites' | 'relationships' | 'elementCatalog',
  icon: LucideIcon,
) {
  return createRoute({
    getParentRoute: () => masterdataRoute,
    path,
    staticData: { crumb },
    component: () => (
      <RequirePermission permission="masterdata:view">
        <ComingSoonPage titleKey={crumb} descriptionKey={`${crumb}.description`} icon={icon} phase={2} />
      </RequirePermission>
    ),
  })
}

const partsRoute = masterdataPage('parts', 'parts', Package)
const partnersRoute = masterdataPage('partners', 'partners', Users)
const sitesRoute = masterdataPage('sites', 'sites', MapPin)
const relationshipsRoute = masterdataPage('relationships', 'relationships', GitFork)
const elementCatalogRoute = masterdataPage('element-catalog', 'elementCatalog', ListChecks)

const cockpitRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cockpit',
  staticData: { crumb: 'cockpit' },
  component: () => (
    <RequirePermission permission="cockpit:view">
      <ComingSoonPage titleKey="cockpit" descriptionKey="cockpit.description" icon={Cable} phase={6} showSystemLink />
    </RequirePermission>
  ),
})

const dataspaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dataspace',
  staticData: { crumb: 'dataspace' },
  component: () => (
    <RequirePermission permission="dataspace:view">
      <ComingSoonPage titleKey="dataspace" descriptionKey="dataspace.description" icon={Boxes} phase={6} />
    </RequirePermission>
  ),
})

const systemRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/system',
  staticData: { crumb: 'system' },
  component: () => (
    <RequirePermission permission="system:view">
      <SystemStatusPage />
    </RequirePermission>
  ),
})

export const routeTree = rootRoute.addChildren([
  dashboardRoute,
  approvalsRoute,
  masterdataRoute.addChildren([
    masterdataIndexRoute,
    partsRoute,
    partnersRoute,
    sitesRoute,
    relationshipsRoute,
    elementCatalogRoute,
  ]),
  cockpitRoute,
  dataspaceRoute,
  systemRoute,
])

export function createAppRouter(context: RouterContext, history?: RouterHistory) {
  return createRouter({
    routeTree,
    context,
    history,
    defaultPreload: 'intent',
    scrollRestoration: true,
  })
}

declare module '@tanstack/react-router' {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
