import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { renderWithProviders } from '@/test/render'

import type { UsageMonth } from './api'
import { UsageChart } from './UsageChart'
import { UsagePage } from './UsagePage'
import { addMonths, fillMonths, lastMonths, niceScale, totals } from './usage-model'

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('@/features/approvals/components/documents', () => ({ saveBlob }))

const nov: UsageMonth = {
  month: '2026-11',
  approvalCases: 2,
  revisions: 1,
  details: { APPROVAL_CASES_SUBMITTED: 1, APPROVAL_CASES_RECEIVED: 1, REVISIONS_SUBMITTED: 1 },
}

describe('usage model', () => {
  it('builds the month range and fills gaps with zeros', () => {
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(lastMonths(12, '2026-11')).toEqual({ from: '2025-12', to: '2026-11' })
    const filled = fillMonths([nov], '2026-09', '2026-11')
    expect(filled.map((m) => [m.month, m.approvalCases])).toEqual([
      ['2026-09', 0],
      ['2026-10', 0],
      ['2026-11', 2],
    ])
    expect(totals(filled)).toEqual({ approvalCases: 2, revisions: 1 })
  })

  it('uses nice axis ticks', () => {
    expect(niceScale(0).max).toBe(4)
    expect(niceScale(7)).toEqual({ max: 8, ticks: [0, 2, 4, 6, 8] })
    expect(niceScale(2).ticks).toEqual([0, 1, 2])
  })
})

describe('<UsageChart>', () => {
  it('draws one bar group per month and labels the chart with a summary', () => {
    renderWithProviders(<UsageChart data={fillMonths([nov], '2026-10', '2026-11')} summary="Zusammenfassung" />)
    const chart = screen.getByRole('img', { name: 'Zusammenfassung' })
    expect(chart.querySelectorAll('[data-month]')).toHaveLength(2)
    // November: two series with values → two bars; October: none
    expect(chart.querySelectorAll('[data-month="2026-11"] path[d^="M"]')).toHaveLength(2)
    expect(screen.getByText('Freigabevorgänge')).toBeInTheDocument()
  })
})

describe('<UsagePage>', () => {
  const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
    const u = String(url)
    if (u.includes('/export.csv'))
      return new Response('month;approvalCases\n2026-11;2\n', { headers: { 'Content-Type': 'text/csv' } })
    if (u.includes('/recompute') && init?.method === 'POST')
      return new Response(
        JSON.stringify({ consistent: true, differences: [], events: 3, recomputedAt: '2026-11-20T10:00:00Z' }),
        {
          headers: { 'Content-Type': 'application/json' },
        },
      )
    return new Response(JSON.stringify([nov]), { headers: { 'Content-Type': 'application/json' } })
  })
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-20T12:00:00'))
    vi.stubGlobal('fetch', fetchMock)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('shows the table, exports CSV and recomputes after confirmation', async () => {
    renderWithProviders(<UsagePage />)
    const table = await screen.findByTestId('usage-table')
    const row = within(table).getByRole('row', { name: /November 2026/ })
    expect(row).toHaveTextContent('2')
    expect(within(table).getAllByRole('row')).toHaveLength(13) // header + 12 months
    expect(String(fetchMock.mock.calls[0]![0])).toContain('/api/v1/usage?from=2025-12&to=2026-11')

    await userEvent.click(screen.getByRole('button', { name: 'CSV exportieren' }))
    await vi.waitFor(() => expect(saveBlob).toHaveBeenCalledWith(expect.anything(), 'usage-2025-12-2026-11.csv'))

    await userEvent.click(screen.getByRole('button', { name: 'Neu berechnen' }))
    const dialog = await screen.findByRole('alertdialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Neu berechnen' }))
    await vi.waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([u, i]) => String(u).endsWith('/api/v1/usage/recompute') && i?.method === 'POST'),
      ).toBe(true),
    )
  })
})
