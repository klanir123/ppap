import { QueryClient } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ConfigResponse, MeResponse, SystemStatusResponse } from '@/api/generated/model'
import i18n from '@/lib/i18n'

import { App } from './App'

const authState = vi.hoisted(() => ({ isAuthenticated: true }))

vi.mock('react-oidc-context', () => ({
  AuthProvider: ({ children }: { children: ReactNode }) => children,
  useAuth: () => ({
    isLoading: false,
    isAuthenticated: authState.isAuthenticated,
    activeNavigator: undefined,
    error: undefined,
    user: authState.isAuthenticated ? { access_token: 'test-token' } : null,
    events: { addUserSignedOut: () => () => {} },
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
    removeUser: vi.fn(),
  }),
}))

const config: ConfigResponse = {
  participant: { bpnl: 'BPNL0000000TIER1', name: 'Zulieferer Tier-1 GmbH', roleLabel: 'Tier-1' },
  oidc: { authority: 'http://localhost:8180/realms/ppap', clientId: 'ppap-frontend' },
  defaultLocale: 'de',
  version: '0.1.0-test',
}

function me(roles: string[]): MeResponse {
  return {
    userId: 'u-1',
    username: 'qm',
    displayName: 'Erika Mustermann',
    email: 'erika@example.com',
    roles,
    tenant: config.participant,
  }
}

const status: SystemStatusResponse = {
  components: [
    { name: 'database', status: 'UP', details: {} },
    { name: 'edc', status: 'DOWN', details: {} },
    { name: 'objectStorage', status: 'UP', details: {} },
    { name: 'dtr', status: 'UNKNOWN', details: {} },
    { name: 'messageBroker', status: 'UP', details: {} },
  ],
}

function mockBackend(roles: string[]) {
  const json = (body: unknown) =>
    new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } })
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input)
    if (url.endsWith('/api/v1/config')) return json(config)
    if (url.endsWith('/api/v1/me')) return json(me(roles))
    if (url.endsWith('/api/v1/system/status')) return json(status)
    return new Response(null, { status: 404 })
  })
}

function renderApp() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<App queryClient={queryClient} />)
}

describe('App shell', () => {
  beforeEach(async () => {
    authState.isAuthenticated = true
    window.history.replaceState({}, '', '/')
    await i18n.changeLanguage('de')
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the participant context from the runtime config', async () => {
    mockBackend(['SUPPLIER_QUALITY_MANAGER'])
    renderApp()

    const context = await screen.findByRole('region', { name: 'Teilnehmerkontext' })
    expect(within(context).getByText('Zulieferer Tier-1 GmbH')).toBeInTheDocument()
    expect(within(context).getByText('BPNL0000000TIER1')).toBeInTheDocument()
    expect(within(context).getByText('Tier-1')).toBeInTheDocument()
    expect(within(context).getByRole('button', { name: 'BPNL kopieren' })).toBeInTheDocument()

    // participant accent applied deterministically (Tier-1 → teal)
    expect(document.documentElement.style.getPropertyValue('--participant-hue')).toBe('185')
    expect(document.title).toContain('Tier-1')
  })

  it('renders the dashboard with greeting, empty state and live system status', async () => {
    mockBackend(['SUPPLIER_QUALITY_MANAGER'])
    renderApp()

    expect(await screen.findByRole('heading', { level: 1, name: 'Guten Tag, Erika Mustermann' })).toBeInTheDocument()
    expect(screen.getByText('Noch keine Freigabevorgänge')).toBeInTheDocument()
    expect(await screen.findByRole('status', { name: 'Status: Gestört' })).toBeInTheDocument()
    // unknown component names from the backend fall back to the raw name
    expect(screen.getByText('messageBroker')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zum Inhalt springen' })).toHaveAttribute('href', '#main-content')
  })

  it('hides the connector cockpit for non-admins and shows it for admins', async () => {
    const spy = mockBackend(['CUSTOMER_QUALITY_ENGINEER'])
    const { unmount } = renderApp()
    expect(await screen.findByRole('link', { name: 'Freigabevorgänge' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Connector-Cockpit' })).not.toBeInTheDocument()
    unmount()
    spy.mockRestore()

    mockBackend(['CONNECTOR_ADMIN'])
    renderApp()
    expect(await screen.findByRole('link', { name: 'Connector-Cockpit' })).toBeInTheDocument()
  })

  it('shows the login landing page without redirecting when signed out', async () => {
    authState.isAuthenticated = false
    const spy = mockBackend([])
    renderApp()

    expect(await screen.findByRole('heading', { name: 'Willkommen' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Anmelden/ })).toBeInTheDocument()
    expect(screen.getByText('BPNL0000000TIER1')).toBeInTheDocument()
    expect(spy.mock.calls.map(([url]) => String(url))).not.toContain('/api/v1/me')
  })
})
