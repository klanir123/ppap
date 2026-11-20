import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { renderWithProviders } from '@/test/render'

import { toCatalog, toContractDefinition, toProcess } from '../api/connector'
import type { ConnectorLogEvent } from '../api/types'
import { createLiveBus, LiveBusContext } from '../live/live-bus'
import { extractConstraints, shortTerm } from '../model/odrl'
import { offersForAsset } from './asset-offers'
import { CatalogResult } from './CatalogTab'
import { filterLog, toLogEntry } from './live-log'
import { LiveLogTab } from './LiveLogTab'

const policy = {
  '@id': 'ppap-usage-default-8d8fc92c',
  policy: {
    permission: [
      {
        action: 'use',
        constraint: {
          and: [
            { leftOperand: 'FrameworkAgreement', operator: 'eq', rightOperand: 'DataExchangeGovernance:1.0' },
            { leftOperand: 'cx-policy:UsagePurpose', operator: 'odrl:isAnyOf', rightOperand: ['cx.quality.base:1'] },
          ],
        },
      },
    ],
  },
}

describe('ODRL reading', () => {
  it('flattens nested constraints and shortens terms but keeps versioned values', () => {
    expect(extractConstraints(policy)).toEqual([
      {
        rule: 'permission',
        action: 'use',
        leftOperand: 'FrameworkAgreement',
        operator: 'eq',
        rightOperand: 'DataExchangeGovernance:1.0',
      },
      {
        rule: 'permission',
        action: 'use',
        leftOperand: 'UsagePurpose',
        operator: 'isAnyOf',
        rightOperand: 'cx.quality.base:1',
      },
    ])
    expect(shortTerm('https://w3id.org/catenax/policy/FrameworkAgreement')).toBe('FrameworkAgreement')
    expect(extractConstraints(null)).toEqual([])
  })

  it('normalises raw EDC objects (contract definition, process, catalog)', () => {
    expect(
      toContractDefinition({
        id: 'cd-1',
        managed: true,
        content: {
          'edc:accessPolicyId': 'acc',
          contractPolicyId: 'use',
          assetsSelector: [{ operandRight: 'ppap-dtr' }],
        },
      }),
    ).toMatchObject({ accessPolicyId: 'acc', contractPolicyId: 'use' })
    expect(
      toProcess({ id: 'n-1', state: 'FINALIZED', partner: { bpnl: 'B1', name: 'OEM' }, counterPartyId: 'did:web:x' }),
    ).toMatchObject({
      state: 'FINALIZED',
      counterParty: { bpnl: 'B1', name: 'OEM', participantId: 'did:web:x' },
    })
    const catalog = toCatalog(
      {
        partner: { bpnl: 'B1', name: 'Demo-OEM AG' },
        durationMs: 267,
        requestedAt: 'x',
        datasets: [{ assetId: 'a', properties: {}, offers: [policy] }],
      },
      'B1',
    )
    expect(catalog.datasets[0]!.offers[0]!.offerId).toBe('ppap-usage-default-8d8fc92c')
  })

  it('joins assets with the policies of the contract definitions that offer them', () => {
    const defs = [
      {
        id: 'cd-1',
        managed: true,
        accessPolicyId: 'acc',
        contractPolicyId: 'use',
        assetsSelector: [{ operandRight: 'ppap-dtr' }],
      },
      {
        id: 'cd-2',
        managed: true,
        accessPolicyId: 'acc',
        contractPolicyId: 'use2',
        assetsSelector: [{ operandRight: 'other' }],
      },
    ]
    const offers = offersForAsset('ppap-dtr', defs, [{ id: 'use', managed: true, policy }])
    expect(offers.map((o) => o.definition.id)).toEqual(['cd-1'])
    expect(offers[0]!.contractPolicy?.id).toBe('use')
  })
})

describe('<CatalogResult>', () => {
  it('renders datasets with dct:type, constraint chips and the request duration', () => {
    renderWithProviders(
      <CatalogResult
        catalog={{
          partnerBpnl: 'BPNL00000000OEM1',
          partnerName: 'Demo-OEM AG',
          durationMs: 267,
          datasets: [
            {
              assetId: 'ppap-notification-receive',
              type: 'https://w3id.org/catenax/taxonomy#PpapNotificationAPI',
              offers: [{ offerId: 'o-1', policy }],
            },
          ],
        }}
      />,
    )
    expect(screen.getByTestId('catalog-duration')).toHaveTextContent('Antwort in 267 ms')
    const dataset = screen.getByRole('article', { name: 'ppap-notification-receive' })
    expect(dataset).toHaveTextContent('dct:type · PpapNotificationAPI')
    expect(within(dataset).getByRole('list', { name: 'Einschränkungen der Policy' })).toHaveTextContent(
      /FrameworkAgreement.*DataExchangeGovernance:1\.0/,
    )
    expect(screen.getByText('1 Angebot')).toBeInTheDocument()
  })
})

const log = (over: Partial<ConnectorLogEvent>): ConnectorLogEvent => ({
  type: 'connector.log',
  at: '2026-11-20T10:00:00Z',
  level: 'INFO',
  correlationId: 'PPF-2026-00017/REV-1',
  messageType: 'PPAP_SUBMISSION',
  step: 'CATALOG',
  text: '→ BPNL00000000OEM1 PPAP_SUBMISSION: CATALOG OK',
  ...over,
})

describe('live log', () => {
  it('filters by minimum level and text (incl. correlation id)', () => {
    const entries = [
      log({}),
      log({ level: 'ERROR', step: 'DELIVERY', correlationId: 'X-9' }),
      log({ level: 'DEBUG' }),
    ].map(toLogEntry)
    expect(filterLog(entries, 'ALL', '')).toHaveLength(3)
    expect(filterLog(entries, 'INFO', '')).toHaveLength(2)
    expect(filterLog(entries, 'ERROR', '')).toHaveLength(1)
    expect(filterLog(entries, 'ALL', 'x-9')).toHaveLength(1)
  })

  it('collects connector.log events, pauses and filters', async () => {
    const bus = createLiveBus()
    renderWithProviders(
      <LiveBusContext value={bus}>
        <LiveLogTab />
      </LiveBusContext>,
    )
    act(() => bus.publish(log({})))
    act(() => bus.publish(log({ level: 'ERROR', text: 'DELIVERY FAILED', correlationId: 'C-2' })))
    expect(within(screen.getByTestId('live-log')).getAllByRole('listitem')).toHaveLength(2)

    await userEvent.type(screen.getByRole('textbox', { name: 'Log durchsuchen' }), 'failed')
    expect(within(screen.getByTestId('live-log')).getAllByRole('listitem')).toHaveLength(1)
    await userEvent.clear(screen.getByRole('textbox', { name: 'Log durchsuchen' }))

    await userEvent.click(screen.getByRole('button', { name: 'Pausieren' }))
    act(() => bus.publish(log({ text: 'while paused' })))
    expect(screen.getByText('1 neuer Eintrag pausiert')).toBeInTheDocument()
    expect(screen.queryByText('while paused')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Fortsetzen' }))
    expect(screen.getByText('while paused')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Korrelations-ID kopieren' }).length).toBeGreaterThan(0)
  })
})
