import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from '@tanstack/react-router'
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { MeResponse } from '@/api/generated/model'
import { CurrentUserContext } from '@/features/auth/current-user'
import { renderWithProviders } from '@/test/render'

import { approvalCase } from '../__tests__/fixtures'
import type { ApprovalCase } from '../api/types'
import { CaseActions } from './CaseActions'

function renderActions(c: ApprovalCase, roles: string[]) {
  const me: MeResponse = { userId: 'u', roles, tenant: { bpnl: 'BPNL00000003AYRE', name: 'OEM', roleLabel: 'OEM' } }
  const rootRoute = createRootRoute({
    component: () => (
      <CurrentUserContext.Provider value={me}>
        <CaseActions approvalCase={c} />
      </CurrentUserContext.Provider>
    ),
  })
  const router = createRouter({ routeTree: rootRoute, history: createMemoryHistory({ initialEntries: ['/'] }) })
  return renderWithProviders(<RouterProvider router={router as never} />)
}

const buttons = async () => {
  await screen.findByRole('group', { name: 'Aktionen' }).catch(() => undefined)
  return screen.queryAllByRole('button').map((b) => b.textContent)
}

describe('<CaseActions>', () => {
  it('renders only actions allowed by the server and covered by the user roles', async () => {
    renderActions(approvalCase({ status: 'DRAFT', allowedActions: ['SEND', 'EDIT', 'CANCEL'] }), [
      'CUSTOMER_QUALITY_ENGINEER',
    ])
    expect(await buttons()).toEqual(['Anfordern', 'Entwurf bearbeiten', 'Abbrechen'])
  })

  it('hides actions the user role does not cover even if allowed', async () => {
    renderActions(approvalCase({ status: 'DRAFT', allowedActions: ['SEND', 'CANCEL'] }), ['CONNECTOR_ADMIN'])
    expect(await buttons()).toEqual([])
  })

  it('offers start work and the package workspace to suppliers', async () => {
    renderActions(approvalCase({ ownRole: 'SUPPLIER', status: 'REQUESTED', allowedActions: ['START_WORK'] }), [
      'SUPPLIER_QUALITY_MANAGER',
    ])
    expect(await buttons()).toEqual(['Bearbeitung starten'])
  })

  it('links suppliers in progress to the package workspace (primary)', async () => {
    renderActions(approvalCase({ ownRole: 'SUPPLIER', status: 'IN_PROGRESS', allowedActions: ['SUBMIT'] }), [
      'SUPPLIER_QUALITY_MANAGER',
    ])
    expect(await buttons()).toEqual(['PPF-Paket bearbeiten'])
  })

  it('offers start review for submitted revisions and nothing for unknown actions', async () => {
    renderActions(
      approvalCase({
        ownRole: 'CUSTOMER',
        status: 'SUBMITTED',
        allowedActions: ['START_REVIEW', 'FUTURE_ACTION' as never],
      }),
      ['CUSTOMER_QUALITY_ENGINEER'],
    )
    expect(await buttons()).toEqual(['Prüfung starten'])
  })
})
