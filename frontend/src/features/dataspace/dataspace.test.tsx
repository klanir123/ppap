import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { renderWithProviders } from '@/test/render'

import type { DataspaceMessage, Topology } from './api'
import { DataspaceGraph } from './DataspaceGraph'
import { flowCategory, flowDirection, messageFromEvent, progress, replaySchedule, toAnimation } from './model/flows'
import { layoutTopology, partnerGroup, rectEdgePoint, VIEW } from './model/layout'

const topology: Topology = {
  self: { bpnl: 'BPNL000000TIER01', name: 'Demo-Tier1 GmbH', roleLabel: 'Tier-1', connector: { status: 'UP' } },
  partners: [
    {
      bpnl: 'BPNL000000TIER02',
      name: 'Demo-Tier2 KG',
      roles: ['SUPPLIER'],
      exchangeReady: true,
      relation: { partnerRole: 'SUPPLIER', exchangeMode: 'PUSH', active: true, policyProfile: 'default' },
      relations: [],
    },
    {
      bpnl: 'BPNL00000000OEM1',
      name: 'Demo-OEM AG',
      roles: ['CUSTOMER'],
      exchangeReady: true,
      relation: {
        partnerRole: 'CUSTOMER',
        exchangeMode: 'PUSH_NOTIFY_PULL_PAYLOAD',
        active: true,
        policyProfile: 'default',
      },
      relations: [],
    },
    {
      bpnl: 'BPNL0000000CUST2',
      name: 'Beispiel Nutzfahrzeuge AG',
      roles: ['CUSTOMER'],
      exchangeReady: false,
      relation: { partnerRole: 'CUSTOMER', exchangeMode: 'PUSH', active: true, policyProfile: 'default' },
      relations: [],
    },
  ],
}

const message = (over: Partial<DataspaceMessage> = {}): DataspaceMessage => ({
  id: 'r-1',
  recordId: 'rec-1',
  steps: [],
  at: '2026-11-20T10:00:00Z',
  direction: 'OUTBOUND',
  messageType: 'PPAP_REQUEST',
  status: 'DELIVERED',
  partner: { bpnl: 'BPNL000000TIER02', name: 'Demo-Tier2 KG' },
  caseNumber: 'PPF-2026-00018',
  ...over,
})

describe('dataspace layout', () => {
  it('places the own connector in the centre, customers above, suppliers below – deterministically', () => {
    const a = layoutTopology(topology)
    const b = layoutTopology({ ...topology, partners: [...topology.partners].reverse() })
    expect(a).toEqual(b)
    const self = a.nodes.find((n) => n.group === 'self')!
    expect(self).toMatchObject({ x: VIEW.width / 2, y: VIEW.height / 2 })
    const oem = a.nodes.find((n) => n.id === 'BPNL00000000OEM1')!
    const tier2 = a.nodes.find((n) => n.id === 'BPNL000000TIER02')!
    expect(oem.y).toBeLessThan(self.y)
    expect(tier2.y).toBeGreaterThan(self.y)
    expect(tier2.x).toBe(self.x)
    // customers sorted by name: "Beispiel …" left of "Demo-OEM AG"
    expect(a.nodes.find((n) => n.id === 'BPNL0000000CUST2')!.x).toBeLessThan(oem.x)
    expect(a.edges).toHaveLength(3)
    expect(
      partnerGroup({
        ...topology.partners[0]!,
        relation: { partnerRole: 'BOTH', exchangeMode: 'PUSH', active: true, policyProfile: 'default' },
        roles: ['CUSTOMER', 'SUPPLIER'],
      }),
    ).toBe('other')
  })

  it('starts edges on the node borders', () => {
    expect(rectEdgePoint({ x: 0, y: 0, w: 100, h: 50 }, { x: 0, y: 200 }, 0)).toEqual({ x: 0, y: 25 })
  })
})

describe('message → animation', () => {
  const partners = new Set(['BPNL000000TIER02'])

  it('maps message kinds to categories and the data direction', () => {
    expect(flowCategory('PPAP_SUBMISSION_AVAILABLE')).toBe('package')
    expect(flowCategory('PULL_DOCUMENT')).toBe('pull')
    expect(flowCategory('TWIN_REGISTRATION')).toBeUndefined()
    expect(flowDirection(message())).toBe('toPartner')
    expect(flowDirection(message({ direction: 'INBOUND' }))).toBe('toSelf')
    // pulls: the data travels to the pulling (own) connector
    expect(flowDirection(message({ messageType: 'PULL_SUBMISSION', direction: 'OUTBOUND' }))).toBe('toSelf')
  })

  it('creates animations only for known partners and edge-bound kinds', () => {
    expect(toAnimation(message(), 100, partners)).toMatchObject({
      category: 'request',
      direction: 'toPartner',
      start: 100,
    })
    expect(toAnimation(message({ partner: { bpnl: 'UNKNOWN' } }), 0, partners)).toBeUndefined()
    expect(toAnimation(message({ messageType: 'TWIN_REGISTRATION' }), 0, partners)).toBeUndefined()
    expect(progress({ start: 0, duration: 1000 }, 500)).toBeCloseTo(0.5)
    expect(progress({ start: 0, duration: 1000 }, 2000)).toBe(1)
  })

  it('reads live events (connector.log carries kind, direction and partner)', () => {
    expect(
      messageFromEvent({
        type: 'connector.log',
        at: 'x',
        level: 'INFO',
        text: 't',
        messageId: 'm',
        messageType: 'PPAP_DECISION',
        direction: 'OUTBOUND',
        partnerBpnl: 'B',
      }),
    ).toMatchObject({ id: 'm', messageType: 'PPAP_DECISION', partner: { bpnl: 'B' } })
    expect(messageFromEvent({ type: 'exchange.changed', messageId: 'm' })).toBeUndefined()
  })

  it('replays the last 15 minutes in order with readable gaps', () => {
    const now = Date.parse('2026-11-20T10:15:00Z')
    const schedule = replaySchedule(
      [
        message({ id: 'b', at: '2026-11-20T10:10:00Z' }),
        message({ id: 'a', at: '2026-11-20T10:10:00Z' }),
        message({ id: 'old', at: '2026-11-20T09:00:00Z' }),
      ],
      now,
    )
    expect(schedule.map((s) => s.message.id)).toEqual(['b', 'a'])
    expect(schedule[1]!.offset - schedule[0]!.offset).toBeGreaterThanOrEqual(1000)
  })
})

describe('<DataspaceGraph>', () => {
  const layout = layoutTopology(topology)
  const animation = toAnimation(message(), 0, new Set(['BPNL000000TIER02']))!

  it('renders nodes for self and partners and a travelling message', () => {
    renderWithProviders(
      <DataspaceGraph
        topology={topology}
        layout={layout}
        animations={[animation]}
        now={1000}
        reducedMotion={false}
        lastByPartner={new Map()}
      />,
    )
    expect(screen.getByTestId('dataspace-self')).toBeInTheDocument()
    expect(screen.getAllByTestId('dataspace-partner')).toHaveLength(3)
    expect(screen.getAllByTestId('dataspace-edge')).toHaveLength(3)
    expect(screen.getByTestId('dataspace-comet')).toHaveAttribute('data-category', 'request')
    expect(screen.queryByTestId('dataspace-highlight')).not.toBeInTheDocument()
  })

  it('with reduced motion highlights the edge instead of moving dots', () => {
    renderWithProviders(
      <DataspaceGraph
        topology={topology}
        layout={layout}
        animations={[animation]}
        now={1000}
        reducedMotion
        lastByPartner={new Map()}
      />,
    )
    expect(screen.queryByTestId('dataspace-comet')).not.toBeInTheDocument()
    expect(screen.getByTestId('dataspace-highlight')).toHaveTextContent('Anforderung')
    expect(screen.getByTestId('dataspace-ring')).toBeInTheDocument()
  })
})
