import type { QueryClient } from '@tanstack/react-query'
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
  type RouterHistory,
} from '@tanstack/react-router'
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
import { cockpitSearchSchema } from '@/features/exchange/cockpit/cockpit-search'
import { CockpitPage } from '@/features/exchange/cockpit/CockpitPage'
import { catalogSearchSchema } from '@/features/masterdata/catalog/catalog-search'
import { CatalogPage } from '@/features/masterdata/catalog/CatalogPage'
import { PartnersPage } from '@/features/masterdata/partners/PartnersPage'
import { PartsPage } from '@/features/masterdata/parts/PartsPage'
import { RelationDetailPage } from '@/features/masterdata/relations/RelationDetailPage'
import { RelationsPage } from '@/features/masterdata/relations/RelationsPage'
import { SitesPage } from '@/features/masterdata/sites/SitesPage'
import { NotFoundPage } from '@/features/errors/NotFoundPage'
import { SystemStatusPage } from '@/features/system/SystemStatusPage'
import { DataspacePage } from '@/features/dataspace/DataspacePage'
import { PolicyProfileDetailPage } from '@/features/masterdata/policy-profiles/PolicyProfileDetailPage'
import { PolicyProfilesPage } from '@/features/masterdata/policy-profiles/PolicyProfilesPage'
import { UsagePage } from '@/features/usage/UsagePage'

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

/** Request wizard (UC-03); `caseId` continues an existing draft, `parentId` requests from a sub-supplier (UC-09). */
const approvalNewRoute = createRoute({
  getParentRoute: () => approvalsRoute,
  path: 'new',
  staticData: { crumb: 'newRequest' },
  validateSearch: z.object({
    caseId: z.string().optional().catch(undefined),
    parentId: z.string().optional().catch(undefined),
  }),
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
  validateSearch: cockpitSearchSchema,
  component: () => (
    <RequirePermission permission="cockpit:view">
      <CockpitPage />
    </RequirePermission>
  ),
})

/** `?present=1` (or `true`) = presentation mode for trade-fair screens. */
const presentFlag = z
  .union([z.boolean(), z.number(), z.string()])
  .optional()
  .transform((v) => (v === true || v === 1 || v === '1' || v === 'true' ? true : undefined))
  .catch(undefined)

const dataspaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dataspace',
  staticData: { crumb: 'dataspace' },
  validateSearch: z.object({ present: presentFlag }),
  component: () => (
    <RequirePermission permission="dataspace:view">
      <DataspacePage />
    </RequirePermission>
  ),
})

const policyProfilesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/policy-profiles',
  staticData: { crumb: 'policyProfiles' },
})

const policyProfilesIndexRoute = createRoute({
  getParentRoute: () => policyProfilesRoute,
  path: '/',
  component: () => (
    <RequirePermission permission="policies:manage">
      <PolicyProfilesPage />
    </RequirePermission>
  ),
})

const policyProfileDetailRoute = createRoute({
  getParentRoute: () => policyProfilesRoute,
  path: '$profileId',
  staticData: { crumb: 'detail' },
  component: () => (
    <RequirePermission permission="policies:manage">
      <PolicyProfileDetailPage />
    </RequirePermission>
  ),
})

const usageRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/usage',
  staticData: { crumb: 'usage' },
  component: () => (
    <RequirePermission permission="usage:view">
      <UsagePage />
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
  policyProfilesRoute.addChildren([policyProfilesIndexRoute, policyProfileDetailRoute]),
  dataspaceRoute,
  usageRoute,
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
