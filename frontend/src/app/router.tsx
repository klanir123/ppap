import type { QueryClient } from '@tanstack/react-query'
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
  type RouterHistory,
} from '@tanstack/react-router'
import { Boxes, Cable } from 'lucide-react'
import { z } from 'zod'

import { listSearchSchema } from '@/components/data-table'
import { AppShell } from '@/features/shell/AppShell'
import { CASE_TABS } from '@/features/approvals/detail/case-tabs'
import { CaseDetailPage } from '@/features/approvals/detail/CaseDetailPage'
import { approvalsSearchSchema } from '@/features/approvals/list/approvals-search'
import { ApprovalsPage } from '@/features/approvals/list/ApprovalsPage'
import { PackageWorkspace } from '@/features/approvals/package/PackageWorkspace'
import { RequestWizardPage } from '@/features/approvals/request/RequestWizardPage'
import { ReviewWorkspace } from '@/features/approvals/review/ReviewWorkspace'
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
})

/** List: filters, list state and view (table | board) live in the URL. */
const approvalsIndexRoute = createRoute({
  getParentRoute: () => approvalsRoute,
  path: '/',
  validateSearch: approvalsSearchSchema,
  component: () => (
    <RequirePermission permission="approvals:view">
      <ApprovalsPage />
    </RequirePermission>
  ),
})

/** Request wizard (UC-03); `caseId` continues an existing draft. */
const approvalNewRoute = createRoute({
  getParentRoute: () => approvalsRoute,
  path: 'new',
  staticData: { crumb: 'newRequest' },
  validateSearch: z.object({ caseId: z.string().optional().catch(undefined) }),
  component: () => (
    <RequirePermission permission="approvals:request">
      <RequestWizardPage />
    </RequirePermission>
  ),
})

const approvalCaseRoute = createRoute({
  getParentRoute: () => approvalsRoute,
  path: '$caseId',
  staticData: { crumb: 'detail' },
})

const approvalCaseIndexRoute = createRoute({
  getParentRoute: () => approvalCaseRoute,
  path: '/',
  validateSearch: z.object({
    tab: z.enum(CASE_TABS).optional().catch(undefined),
    revision: z.number().int().min(1).optional().catch(undefined),
  }),
  component: () => (
    <RequirePermission permission="approvals:view">
      <CaseDetailPage />
    </RequirePermission>
  ),
})

/** Supplier workspace (UC-05). */
const approvalPackageRoute = createRoute({
  getParentRoute: () => approvalCaseRoute,
  path: 'package',
  staticData: { crumb: 'package' },
  validateSearch: z.object({ element: z.string().optional().catch(undefined) }),
  component: () => (
    <RequirePermission permission="approvals:supply">
      <PackageWorkspace />
    </RequirePermission>
  ),
})

/** Customer review workspace (UC-07). */
const approvalReviewRoute = createRoute({
  getParentRoute: () => approvalCaseRoute,
  path: 'review',
  staticData: { crumb: 'review' },
  validateSearch: z.object({ element: z.string().optional().catch(undefined) }),
  component: () => (
    <RequirePermission permission="approvals:decide">
      <ReviewWorkspace />
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
  approvalsRoute.addChildren([
    approvalsIndexRoute,
    approvalNewRoute,
    approvalCaseRoute.addChildren([approvalCaseIndexRoute, approvalPackageRoute, approvalReviewRoute]),
  ]),
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
