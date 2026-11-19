import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ConfigResponse } from '@/api/generated/model'
import { ConfigContext } from '@/features/config/config-context'
import i18n from '@/lib/i18n'
import { renderWithProviders } from '@/test/render'

import type { Relation } from '../api/types'
import { RelationFormSheet } from './RelationFormSheet'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ETag: '"4"' } })

const relation: Relation = {
  id: 'rel-1',
  partnerBpnl: 'BPNL00000003B2OM',
  partnerName: 'Demo-OEM AG',
  partnerRole: 'CUSTOMER',
  exchangeMode: 'PUSH_NOTIFY_PULL_PAYLOAD',
  policyProfile: 'default',
  active: true,
  mappingCount: 2,
  version: 3,
}

const config = {
  defaultLocale: 'de',
  version: 'test',
  oidc: { issuer: 'http://kc', clientId: 'ppap' },
  participant: { bpnl: 'BPNL00000003AYRE', name: 'Tier-1', roleLabel: 'Tier-1' },
} as unknown as ConfigResponse

function renderSheet(editing: Relation | 'new' = relation) {
  return renderWithProviders(
    <ConfigContext.Provider value={config}>
      <RelationFormSheet editing={editing} onClose={() => {}} onReloaded={() => {}} readOnly={false} />
    </ConfigContext.Provider>,
  )
}

describe('exchange mode selector (relation)', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  beforeEach(async () => {
    await i18n.changeLanguage('de')
    fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith('/api/v1/partners')) {
        return json({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 })
      }
      if (url === '/api/v1/relations/rel-1' && init?.method === 'PUT') {
        return json({ ...relation, ...JSON.parse(String(init.body)), version: 4 })
      }
      return json({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('explains the three modes and marks the recommended one', () => {
    renderSheet()
    const group = screen.getByRole('radiogroup', { name: 'Austauschmodus' })
    const options = within(group).getAllByRole('radio')
    expect(options).toHaveLength(3)
    const recommended = within(group).getByRole('radio', {
      name: /^Push-Benachrichtigung, Pull-Nutzdaten\s*Empfohlen$/,
    })
    expect(recommended).toHaveAttribute('aria-checked', 'true')
    expect(recommended).toHaveTextContent('Notification + Digital Twin')
    expect(within(group).getByRole('radio', { name: /^Pull$/ })).toHaveTextContent('Abfragezyklus')
    expect(within(group).getByRole('radio', { name: /^Push$/ })).toHaveTextContent('Daten liegen')
    expect(screen.getByText(/gilt ab der nächsten Einreichung/)).toBeInTheDocument()
    // only relevant when we receive from a supplier
    expect(screen.queryByText(/Bei Lieferanten bestimmt der Lieferant/)).not.toBeInTheDocument()
  })

  it('saves the chosen mode via PUT /relations/{id} with If-Match', async () => {
    const user = userEvent.setup()
    renderSheet()
    await user.click(screen.getByRole('radio', { name: /^Pull$/ }))
    await user.click(screen.getByRole('button', { name: 'Speichern' }))
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/relations/rel-1', expect.objectContaining({ method: 'PUT' })),
    )
    const [, init] = fetchMock.mock.calls.find(([, i]) => (i as RequestInit | undefined)?.method === 'PUT')! as [
      string,
      RequestInit,
    ]
    expect(JSON.parse(String(init.body))).toMatchObject({ exchangeMode: 'PULL', partnerRole: 'CUSTOMER' })
    expect(new Headers(init.headers).get('If-Match')).toBe('"3"')
  })

  it('supports arrow keys and shows the supplier hint for supplier relations', async () => {
    const user = userEvent.setup()
    renderSheet({ ...relation, partnerRole: 'SUPPLIER' })
    expect(screen.getByText(/Bei Lieferanten bestimmt der Lieferant/)).toBeInTheDocument()
    await user.click(screen.getByRole('radio', { name: /^Push-Benachrichtigung/ }))
    await user.keyboard('{ArrowDown}')
    const push = screen.getByRole('radio', { name: /^Push$/ })
    expect(push).toHaveFocus()
    await user.keyboard(' ')
    expect(push).toHaveAttribute('aria-checked', 'true')
  })
})
