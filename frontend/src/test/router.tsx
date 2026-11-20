import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from '@tanstack/react-router'
import type { ReactElement } from 'react'

import type { MeResponse } from '@/api/generated/model'
import { CurrentUserContext } from '@/features/auth/current-user'

import { renderWithProviders } from './render'

/** Renders a component inside a memory router (for `<Link>`) as a user with the given roles. */
export function renderInRouter(ui: ReactElement, roles: string[] = []) {
  const me: MeResponse = {
    userId: 'u',
    roles,
    tenant: { bpnl: 'BPNL000000TIER01', name: 'Demo-Tier1 GmbH', roleLabel: 'Tier-1' },
  }
  const rootRoute = createRootRoute({
    component: () => <CurrentUserContext.Provider value={me}>{ui}</CurrentUserContext.Provider>,
  })
  const router = createRouter({ routeTree: rootRoute, history: createMemoryHistory({ initialEntries: ['/'] }) })
  return renderWithProviders(<RouterProvider router={router as never} />)
}
