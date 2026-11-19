import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import i18n from '@/lib/i18n'

import { exchangeRecord, step } from '../__tests__/fixtures'
import { renderAs } from '../__tests__/render'
import { DeliveryBadge } from './Delivery'
import { ExchangeRecordCard } from './ExchangeRecordCard'

const failed = exchangeRecord({
  id: 'r-9',
  status: 'FAILED',
  attempts: 20,
  lastError: 'Data plane answered 502 Bad Gateway',
  steps: [
    step({ step: 'CATALOG', status: 'SKIPPED' }),
    step({ step: 'DELIVERY', status: 'FAILED', details: { httpStatus: 502 } }),
  ],
})

describe('<ExchangeRecordCard>', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })
  afterEach(() => vi.unstubAllGlobals())

  it('renders type, direction, partner, status, compact flow and identifiers', () => {
    renderAs(<ExchangeRecordCard record={exchangeRecord()} />, ['SUPPLIER_QUALITY_MANAGER'])
    const card = screen.getByRole('article', { name: /PPF-Paket/ })
    expect(card).toHaveTextContent('Ausgehend an Demo-OEM AG')
    expect(within(card).getByRole('status', { name: 'Status: Zugestellt' })).toBeInTheDocument()
    expect(card).toHaveTextContent('Katalog')
    expect(card).toHaveTextContent('Zustellung')
    expect(card).toHaveTextContent('5 von 5 Schritten')
    expect(card).toHaveTextContent('PPF-2026-00031/REV-1')
    expect(card).toHaveTextContent('9f2c4e1a7b3d')
  })

  it('expands the step details with EDC ids, HTTP status, cached EDR and durations', async () => {
    renderAs(<ExchangeRecordCard record={exchangeRecord()} />, [])
    await userEvent.click(screen.getByRole('button', { name: /Ablauf anzeigen/ }))
    expect(screen.getByLabelText('Technische Details zu Vertragsverhandlung')).toHaveTextContent('neg-1')
    expect(screen.getByText('agr-1')).toBeInTheDocument()
    expect(screen.getByText('tp-1')).toBeInTheDocument()
    expect(screen.getByText('202')).toBeInTheDocument()
    expect(screen.getByText('EDR aus Cache')).toBeInTheDocument()
    expect(screen.getByText('ja')).toBeInTheDocument()
    expect(screen.getByText(/1,5 s/)).toBeInTheDocument()
  })

  it('shows the last error of a failed message; retry only for connector admins', () => {
    const { unmount } = renderAs(<ExchangeRecordCard record={failed} />, ['CUSTOMER_QUALITY_ENGINEER'])
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Zustellung fehlgeschlagen')
    expect(alert).toHaveTextContent('Data plane answered 502 Bad Gateway')
    expect(screen.queryByRole('button', { name: 'Erneut senden' })).not.toBeInTheDocument()
    unmount()
    renderAs(<ExchangeRecordCard record={failed} />, ['CONNECTOR_ADMIN'])
    expect(screen.getByRole('button', { name: 'Erneut senden' })).toBeInTheDocument()
  })

  it('posts the retry for the message', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    renderAs(<ExchangeRecordCard record={failed} />, ['CONNECTOR_ADMIN'])
    await userEvent.click(screen.getByRole('button', { name: 'Erneut senden' }))
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/exchange/messages/r-9/retry',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('offers "Erneut verarbeiten" for rejected inbound messages', () => {
    renderAs(
      <ExchangeRecordCard
        record={exchangeRecord({
          direction: 'INBOUND',
          status: 'REJECTED',
          lastError: 'PAYLOAD_HASH_MISMATCH',
          steps: [],
        })}
      />,
      ['CONNECTOR_ADMIN'],
    )
    expect(screen.getByRole('alert')).toHaveTextContent('Verarbeitung abgewiesen')
    expect(screen.getByRole('button', { name: 'Erneut verarbeiten' })).toBeInTheDocument()
  })
})

describe('<DeliveryBadge>', () => {
  it.each([
    ['delivered', 'Zugestellt'],
    ['pending', 'Wird zugestellt …'],
    ['failed', 'Zustellung fehlgeschlagen'],
  ] as const)('%s → %s', async (state, label) => {
    await i18n.changeLanguage('de')
    renderAs(<DeliveryBadge state={state} />, [])
    expect(screen.getByText(label)).toBeInTheDocument()
  })
})
