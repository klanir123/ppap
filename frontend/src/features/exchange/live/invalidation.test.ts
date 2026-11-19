import { describe, expect, it } from 'vitest'

import { caseDetail } from '@/features/approvals/__tests__/fixtures'

import { affectedByResume, affectsQuery, parseLiveEvent } from './invalidation'
import { mayNotify, notificationFor } from './notifications'

const C = '/api/v1/approval-cases'

describe('parseLiveEvent', () => {
  it('reads the event name from the SSE event field or the data type', () => {
    expect(parseLiveEvent({ event: 'case.changed', data: '{"caseId":"c-1","status":"SUBMITTED"}' })).toEqual({
      type: 'case.changed',
      caseId: 'c-1',
      status: 'SUBMITTED',
    })
    expect(parseLiveEvent({ event: 'message', data: '{"type":"exchange.changed","caseId":"c-1"}' })).toMatchObject({
      type: 'exchange.changed',
    })
  })

  it('ignores heartbeats, unknown events and invalid JSON', () => {
    expect(parseLiveEvent({ event: 'heartbeat', data: '{}' })).toBeUndefined()
    expect(parseLiveEvent({ event: 'case.changed', data: 'not json' })).toBeUndefined()
    expect(parseLiveEvent({ event: 'case.changed', data: '{}' })).toBeUndefined()
  })
})

describe('affectsQuery', () => {
  const caseEvent = { type: 'case.changed' as const, caseId: 'c-1' }
  const exchangeEvent = { type: 'exchange.changed' as const, caseId: 'c-1', messageId: 'm-1' }

  it('case.changed → lists, stats, case detail incl. sub-resources, audit', () => {
    expect(affectsQuery(caseEvent, [C, { page: 0 }])).toBe(true)
    expect(affectsQuery(caseEvent, [`${C}/stats`])).toBe(true)
    expect(affectsQuery(caseEvent, [`${C}/c-1`])).toBe(true)
    expect(affectsQuery(caseEvent, [`${C}/c-1/submissions/1`])).toBe(true)
    expect(affectsQuery(caseEvent, [`${C}/c-1/exchange`])).toBe(true)
    expect(affectsQuery(caseEvent, ['/api/v1/audit-events?objectId=c-1'])).toBe(true)
    expect(affectsQuery(caseEvent, [`${C}/c-10`])).toBe(false)
    expect(affectsQuery(caseEvent, ['/api/v1/parts'])).toBe(false)
  })

  it('exchange.changed → exchange records of the case and cockpit queries only', () => {
    expect(affectsQuery(exchangeEvent, [`${C}/c-1/exchange`])).toBe(true)
    expect(affectsQuery(exchangeEvent, ['/api/v1/exchange/messages', {}])).toBe(true)
    expect(affectsQuery(exchangeEvent, ['/api/v1/exchange/status'])).toBe(true)
    expect(affectsQuery(exchangeEvent, [`${C}/c-2/exchange`])).toBe(false)
    expect(affectsQuery(exchangeEvent, [`${C}/c-1`])).toBe(false)
    expect(affectsQuery({ type: 'exchange.changed' }, [`${C}/c-2/exchange`])).toBe(true)
  })

  it('resume refreshes all case and exchange data', () => {
    expect(affectedByResume([`${C}/c-9/exchange`])).toBe(true)
    expect(affectedByResume(['/api/v1/exchange/status'])).toBe(true)
    expect(affectedByResume(['/api/v1/config'])).toBe(false)
  })
})

describe('notifications', () => {
  it('only statuses worth a toast trigger a lookup', () => {
    expect(mayNotify({ type: 'case.changed', caseId: 'c', status: 'REQUESTED' })).toBe(true)
    expect(mayNotify({ type: 'case.changed', caseId: 'c', status: 'IN_PROGRESS' })).toBe(false)
    expect(mayNotify({ type: 'exchange.changed', caseId: 'c' })).toBe(false)
  })

  it('new request (supplier, via exchange), decision received, submission received – never own actions', () => {
    const supplier = { ownRole: 'SUPPLIER' as const, source: 'EXCHANGE' as const }
    expect(notificationFor(caseDetail({ ...supplier, status: 'REQUESTED' }))).toMatchObject({
      kind: 'newRequest',
      partner: 'Zulieferer Tier-1 GmbH',
    })
    expect(notificationFor(caseDetail({ ...supplier, status: 'REJECTED' }))).toMatchObject({
      kind: 'decisionReceived',
      result: 'REJECTED',
    })
    expect(notificationFor(caseDetail({ ownRole: 'CUSTOMER', status: 'SUBMITTED' }))).toMatchObject({
      kind: 'submissionReceived',
    })
    expect(notificationFor(caseDetail({ ownRole: 'SUPPLIER', source: 'MANUAL', status: 'REQUESTED' }))).toBeUndefined()
    expect(notificationFor(caseDetail({ ownRole: 'CUSTOMER', status: 'REJECTED' }))).toBeUndefined()
    expect(notificationFor(caseDetail({ ...supplier, status: 'SUBMITTED' }))).toBeUndefined()
  })
})
