import type { QueryClient } from '@tanstack/react-query'
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
  type RouterHistory,
} from '@tanstack/react-router'
import { Boxes, Cable, FileCheck2 } from 'lucide-react'

import { listSearchSchema } from '@/components/data-table'
import { AppShell } from '@/features/shell/AppShell'
import { RequirePermission } from '@/features/auth/RequirePermission'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { catalogSearchSchema } from '@/features/masterdata/catalog/catalog-search'
import { CatalogPage } from '@/features/masterdata/catalog/CatalogPage'
import { PartnersPage } from '@/features/masterdata/partners/PartnersPage'
import { PartsPage } from '@/features/masterdata/parts/PartsPage'
import { RelationDetailPage } from '@/features/masterdata/relations/RelationDetailPage'
import { RelationsPage } from '@/features/masterdata/relations/RelationsPage'
import { SitesPage } from '@/features/masterdata/sites/SitesPage'
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
    crumb?: NavLabelKey | 'detail'
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

/** Master data routes: list state lives in the URL (`?q=&active=&page=&size=&sort=`). */
function masterdataListRoute<const TPath extends string>(
  path: TPath,
  crumb: 'parts' | 'partners' | 'sites',
  Page: () => React.ReactNode,
) {
  return createRoute({
    getParentRoute: () => masterdataRoute,
    path,
    staticData: { crumb },
    validateSearch: listSearchSchema,
    component: () => (
      <RequirePermission permission="masterdata:view">
        <Page />
      </RequirePermission>
    ),
  })
}

const partsRoute = masterdataListRoute('parts', 'parts', PartsPage)
const partnersRoute = masterdataListRoute('partners', 'partners', PartnersPage)
const sitesRoute = masterdataListRoute('sites', 'sites', SitesPage)

const relationshipsRoute = createRoute({
  getParentRoute: () => masterdataRoute,
  path: 'relationships',
  staticData: { crumb: 'relationships' },
})

const relationshipsIndexRoute = createRoute({
  getParentRoute: () => relationshipsRoute,
  path: '/',
  validateSearch: listSearchSchema,
  component: () => (
    <RequirePermission permission="masterdata:view">
      <RelationsPage />
    </RequirePermission>
  ),
})

const relationDetailRoute = createRoute({
  getParentRoute: () => relationshipsRoute,
  path: '$relationId',
  staticData: { crumb: 'detail' },
  validateSearch: listSearchSchema,
  component: () => (
    <RequirePermission permission="masterdata:view">
      <RelationDetailPage />
    </RequirePermission>
  ),
})

const elementCatalogRoute = createRoute({
  getParentRoute: () => masterdataRoute,
  path: 'element-catalog',
  staticData: { crumb: 'elementCatalog' },
  validateSearch: catalogSearchSchema,
  component: () => (
    <RequirePermission permission="masterdata:view">
      <CatalogPage />
    </RequirePermission>
  ),
})

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
    relationshipsRoute.addChildren([relationshipsIndexRoute, relationDetailRoute]),
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
