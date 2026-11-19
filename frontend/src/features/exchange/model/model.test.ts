import { describe, expect, it } from 'vitest'

import { exchangeRecord, step } from '../__tests__/fixtures'
import { canRetry, deriveDelivery, sortNewestFirst } from './status'
import { buildTimeline, totalDuration } from './steps'

describe('buildTimeline', () => {
  it('maps all outbound steps in flow order', () => {
    const rows = buildTimeline(exchangeRecord())
    expect(rows.map((r) => [r.step, r.state])).toEqual([
      ['CATALOG', 'OK'],
      ['NEGOTIATION', 'OK'],
      ['TRANSFER', 'OK'],
      ['EDR', 'OK'],
      ['DELIVERY', 'OK'],
    ])
  })

  it('marks missing steps PENDING while in flight and NOT_REACHED after a failure', () => {
    const steps = [step({ step: 'CATALOG' }), step({ step: 'NEGOTIATION', status: 'FAILED' })]
    expect(buildTimeline(exchangeRecord({ status: 'IN_PROGRESS', steps })).map((r) => r.state)).toEqual([
      'OK',
      'FAILED',
      'PENDING',
      'PENDING',
      'PENDING',
    ])
    expect(buildTimeline(exchangeRecord({ status: 'FAILED', steps })).at(-1)?.state).toBe('NOT_REACHED')
  })

  it('keeps the latest attempt per step and counts retries', () => {
    const steps = [
      step({ step: 'DELIVERY', status: 'FAILED', at: '2026-11-19T09:00:01Z' }),
      step({ step: 'DELIVERY', status: 'OK', at: '2026-11-19T09:00:09Z' }),
    ]
    const delivery = buildTimeline(exchangeRecord({ steps })).find((r) => r.step === 'DELIVERY')!
    expect(delivery.state).toBe('OK')
    expect(delivery.attempts).toBe(2)
  })

  it('shows KEY_FETCH for inbound messages only when it happened', () => {
    const base = { direction: 'INBOUND' as const, status: 'PROCESSED' as const }
    const without = buildTimeline(
      exchangeRecord({
        ...base,
        steps: [step({ step: 'RECEIPT' }), step({ step: 'VALIDATION' }), step({ step: 'PROCESSING' })],
      }),
    )
    expect(without.map((r) => r.step)).toEqual(['RECEIPT', 'VALIDATION', 'PROCESSING'])
    const withKey = buildTimeline(
      exchangeRecord({ ...base, steps: [step({ step: 'RECEIPT' }), step({ step: 'KEY_FETCH' })] }),
    )
    expect(withKey.map((r) => r.step)).toEqual(['RECEIPT', 'VALIDATION', 'KEY_FETCH', 'PROCESSING'])
  })

  it('sums step durations', () => {
    expect(totalDuration(exchangeRecord().steps)).toBe(1920)
    expect(totalDuration([])).toBeUndefined()
  })
})

describe('deriveDelivery', () => {
  const at = (h: number) => `2026-11-19T${String(h).padStart(2, '0')}:00:00Z`

  it('uses the latest outbound message', () => {
    const records = [
      exchangeRecord({ id: 'a', createdAt: at(8), status: 'DELIVERED' }),
      exchangeRecord({ id: 'b', createdAt: at(10), status: 'IN_PROGRESS', messageType: 'PPAP_STATUS_UPDATE' }),
      exchangeRecord({ id: 'c', createdAt: at(11), direction: 'INBOUND', status: 'PROCESSED' }),
    ]
    expect(deriveDelivery(records)).toMatchObject({ state: 'pending', record: { id: 'b' } })
    expect(deriveDelivery(records, 'PPAP_SUBMISSION')).toMatchObject({ state: 'delivered', record: { id: 'a' } })
  })

  it('maps FAILED to failed, PENDING to pending; no outbound → undefined', () => {
    expect(deriveDelivery([exchangeRecord({ status: 'FAILED' })])?.state).toBe('failed')
    expect(deriveDelivery([exchangeRecord({ status: 'PENDING' })])?.state).toBe('pending')
    expect(deriveDelivery([exchangeRecord({ direction: 'INBOUND', status: 'RECEIVED' })])).toBeUndefined()
    expect(deriveDelivery(undefined)).toBeUndefined()
  })

  it('sorts newest first', () => {
    const sorted = sortNewestFirst([
      exchangeRecord({ id: 'old', createdAt: at(1) }),
      exchangeRecord({ id: 'new', createdAt: at(2) }),
    ])
    expect(sorted.map((r) => r.id)).toEqual(['new', 'old'])
  })
})

describe('canRetry', () => {
  it('allows outbound FAILED and inbound REJECTED only', () => {
    expect(canRetry({ direction: 'OUTBOUND', status: 'FAILED' })).toBe(true)
    expect(canRetry({ direction: 'OUTBOUND', status: 'DELIVERED' })).toBe(false)
    expect(canRetry({ direction: 'INBOUND', status: 'REJECTED' })).toBe(true)
    expect(canRetry({ direction: 'INBOUND', status: 'FAILED' })).toBe(false)
  })
})
