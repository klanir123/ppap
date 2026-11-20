import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { TooltipProvider } from '@/components/ui/tooltip'
import { caseDetail } from '@/features/approvals/__tests__/fixtures'

import { exchangeRecord } from '../__tests__/fixtures'
import { FALLBACK_POLL_MS, LIVE_SAFETY_POLL_MS, pollWhileInFlight } from '../api/hooks'
import type { ExchangeRecord } from '../api/types'
import { LiveStatusContext } from '../live/live-context'
import { affectsQuery } from '../live/invalidation'
import { SubmissionDelivery } from './SubmissionDelivery'

const pending = exchangeRecord({ messageType: 'PPAP_SUBMISSION', status: 'PENDING', completedAt: undefined, steps: [] })
const delivered = exchangeRecord({ messageType: 'PPAP_SUBMISSION', status: 'DELIVERED' })

const submitted = caseDetail({
  id: 'c-1',
  ownRole: 'SUPPLIER',
  source: 'EXCHANGE',
  status: 'SUBMITTED',
  submissions: [{ revision: 1, status: 'SUBMITTED', exchangeMode: 'PUSH' }],
} as never)

function setup(responses: ExchangeRecord[][]) {
  let call = 0
  const fetchMock = vi.fn(async () => {
    const body = responses[Math.min(call, responses.length - 1)]
    call += 1
    return new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const rootRoute = createRootRoute({
    component: () => (
      <LiveStatusContext value="open">
        <SubmissionDelivery approvalCase={submitted} />
      </LiveStatusContext>
    ),
  })
  const router = createRouter({ routeTree: rootRoute, history: createMemoryHistory({ initialEntries: ['/'] }) })
  render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterProvider router={router as never} />
      </TooltipProvider>
    </QueryClientProvider>,
  )
  return { fetchMock, queryClient }
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('pollWhileInFlight', () => {
  it('polls while in flight or awaited – slowly as a safety net when live, faster without', () => {
    expect(pollWhileInFlight([delivered], true)).toBe(false)
    expect(pollWhileInFlight([pending], false)).toBe(FALLBACK_POLL_MS)
    expect(pollWhileInFlight([pending], true)).toBe(LIVE_SAFETY_POLL_MS)
    expect(pollWhileInFlight([], true, true)).toBe(LIVE_SAFETY_POLL_MS)
    expect(pollWhileInFlight(undefined, true, false)).toBe(false)
  })
})

describe('<SubmissionDelivery> update path', () => {
  it('shows the delivery after the live event of the case invalidates the records', async () => {
    const { queryClient } = setup([[pending], [delivered]])
    expect((await screen.findAllByText('Ausstehend', { exact: false })).length).toBeGreaterThan(0)
    const event = {
      type: 'exchange.changed' as const,
      caseId: 'c-1',
      messageId: delivered.messageId,
      status: 'DELIVERED' as const,
    }
    await act(() => queryClient.invalidateQueries({ predicate: (q) => affectsQuery(event, q.queryKey) }))
    expect(await screen.findByText('Zugestellt')).toBeInTheDocument()
  })

  it('still reaches "delivered" when the event overtook the stored status (no further event)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { fetchMock } = setup([[pending], [delivered]])
    expect((await screen.findAllByText('Ausstehend', { exact: false })).length).toBeGreaterThan(0)
    await act(() => vi.advanceTimersByTimeAsync(LIVE_SAFETY_POLL_MS + 100))
    expect(await screen.findByText('Zugestellt')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
