import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { caseDetail } from '@/features/approvals/__tests__/fixtures'
import i18n from '@/lib/i18n'

import { exchangeRecord, step } from '../__tests__/fixtures'
import { renderAs } from '../__tests__/render'
import type { TwinDto } from '../api/types'
import { ExchangeModeBadge } from './ExchangeModeBadge'
import { ExchangeRecordCard } from './ExchangeRecordCard'
import { ExchangeTab } from './ExchangeTab'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const EXCHANGE_ID = '6d1f0d7e-1111-4c1c-9a55-000000000017'

const customer = { type: 'ExternalReference', keys: [{ type: 'GlobalReference', value: 'BPNL00000003B2OM' }] }

const twin: TwinDto = {
  exchangeId: EXCHANGE_ID,
  caseId: 'c-1',
  registeredAt: '2026-11-19T09:00:00Z',
  updatedAt: '2026-11-19T09:05:00Z',
  shell: {
    id: `urn:uuid:${EXCHANGE_ID}`,
    idShort: 'ppap-PPF-2026-00017',
    globalAssetId: `urn:uuid:${EXCHANGE_ID}`,
    specificAssetIds: [
      { name: 'manufacturerId', value: 'BPNL00000003AYRE', externalSubjectId: customer },
      { name: 'customerPartId', value: 'D10-4711', externalSubjectId: customer },
      { name: 'manufacturerPartId', value: 'T1-310000-01', externalSubjectId: customer },
      { name: 'ppapExchangeId', value: EXCHANGE_ID, externalSubjectId: customer },
    ],
    submodelDescriptors: [
      {
        id: 'urn:uuid:aaaaaaaa-0000-0000-0000-000000000001',
        idShort: 'PpapSubmission-1',
        semanticId: {
          type: 'ExternalReference',
          keys: [{ type: 'GlobalReference', value: 'urn:samm:com.msg.ppap:0.1.0#PpapSubmission' }],
        },
        endpoints: [
          {
            interface: 'SUBMODEL-3.0',
            protocolInformation: {
              href: `http://tier1-edc-dp:8081/api/public/${EXCHANGE_ID}/1`,
              subprotocol: 'DSP',
              subprotocolBody:
                'id=ppap-submission-BPNL00000003B2OM;dspEndpoint=http://tier1-edc-cp:8084/api/v1/dsp/2025-1',
            },
          },
        ],
      },
    ],
  },
}

const supplierCase = caseDetail({
  ownRole: 'SUPPLIER',
  source: 'EXCHANGE',
  status: 'SUBMITTED',
  exchangeId: EXCHANGE_ID,
})

describe('exchange tab – digital twin panel (supplier)', () => {
  let twinResponse: () => Response
  beforeEach(async () => {
    await i18n.changeLanguage('de')
    twinResponse = () => json(twin)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/exchange')) return json([])
        if (url === `/api/v1/twins/${EXCHANGE_ID}`) return twinResponse()
        return json({}, 404)
      }),
    )
  })
  afterEach(() => vi.unstubAllGlobals())

  it('shows shell id, visibility and – expanded – identifiers and descriptors per revision', async () => {
    const user = userEvent.setup()
    renderAs(<ExchangeTab approvalCase={supplierCase} />, ['SUPPLIER_QUALITY_MANAGER'])
    const panel = await screen.findByRole('region', { name: 'Digital Twin' })
    expect(await within(panel).findByText(`urn:uuid:${EXCHANGE_ID}`)).toBeInTheDocument()
    expect(within(panel).getByText('BPNL00000003B2OM')).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Technische Details anzeigen' }))
    const rows = within(panel)
      .getAllByRole('rowheader')
      .map((r) => r.textContent)
    expect(rows).toEqual([
      'KundensachnummercustomerPartId',
      'LieferantensachnummermanufacturerPartId',
      'Hersteller (BPNL)manufacturerId',
      'Austausch-IDppapExchangeId',
    ])
    expect(within(panel).getByText('Revision 1')).toBeInTheDocument()
    expect(within(panel).getByText('urn:samm:com.msg.ppap:0.1.0#PpapSubmission')).toBeInTheDocument()
    expect(within(panel).getByText(/^id=ppap-submission-BPNL00000003B2OM;dspEndpoint=/)).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Zugang (subprotocolBody) kopieren' })).toBeInTheDocument()
  })

  it('empty state before the first submission (404)', async () => {
    twinResponse = () => json({ code: 'TWIN_NOT_FOUND' }, 404)
    renderAs(<ExchangeTab approvalCase={supplierCase} />, ['CONNECTOR_ADMIN'])
    const panel = await screen.findByRole('region', { name: 'Digital Twin' })
    expect(await within(panel).findByText('Noch kein Digital Twin')).toBeInTheDocument()
  })

  it('explains that push mode registers no twin', async () => {
    twinResponse = () => json({ code: 'TWIN_NOT_FOUND' }, 404)
    const pushCase = {
      ...supplierCase,
      submissions: [{ revision: 1, status: 'SUBMITTED' as const, exchangeMode: 'PUSH' as const }],
    }
    renderAs(<ExchangeTab approvalCase={pushCase} />, ['SUPPLIER_QUALITY_MANAGER'])
    expect(await screen.findByText(/Im Modus Push wird kein Digital Twin registriert/)).toBeInTheDocument()
  })

  it('is hidden for customers and for roles without twin access', async () => {
    const { unmount } = renderAs(<ExchangeTab approvalCase={{ ...supplierCase, ownRole: 'CUSTOMER' }} />, [
      'CONNECTOR_ADMIN',
    ])
    await screen.findByText('Noch kein Datenaustausch')
    expect(screen.queryByRole('region', { name: 'Digital Twin' })).not.toBeInTheDocument()
    unmount()
    renderAs(<ExchangeTab approvalCase={supplierCase} />, ['MASTERDATA_MANAGER'])
    await screen.findByText('Noch kein Datenaustausch')
    expect(screen.queryByRole('region', { name: 'Digital Twin' })).not.toBeInTheDocument()
  })
})

describe('pull records in the exchange tab', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it('renders a package pull with DTR steps and explanations', async () => {
    const user = userEvent.setup()
    const record = exchangeRecord({
      direction: 'INBOUND',
      messageType: 'PULL_SUBMISSION',
      status: 'PROCESSED',
      partner: { bpnl: 'BPNL00000003AYRE', name: 'Zulieferer Tier-1 GmbH' },
      steps: [
        step({ step: 'CATALOG', target: 'DTR', details: { assetId: 'ppap-dtr' } }),
        step({ step: 'EDR', target: 'DTR', at: '2026-11-19T09:00:01Z', details: { cachedEdr: true } }),
        step({ step: 'DTR_LOOKUP', target: 'DTR', at: '2026-11-19T09:00:02Z', details: { shellId: 'urn:uuid:x' } }),
        step({ step: 'SHELL_DESCRIPTOR', target: 'DTR', at: '2026-11-19T09:00:03Z' }),
        step({ step: 'CATALOG', target: 'SUBMODEL', targetRef: 'urn:uuid:s-1', at: '2026-11-19T09:00:04Z' }),
        step({ step: 'EDR', target: 'SUBMODEL', at: '2026-11-19T09:00:04Z' }),
        step({ step: 'SUBMODEL_FETCH', target: 'SUBMODEL', at: '2026-11-19T09:00:05Z', details: { httpStatus: 200 } }),
        step({ step: 'HASH_CHECK', target: 'SUBMODEL', at: '2026-11-19T09:00:06Z' }),
        step({ step: 'SIGNATURE_CHECK', target: 'SUBMODEL', at: '2026-11-19T09:00:07Z' }),
        step({ step: 'PROCESSING', target: 'SUBMODEL', at: '2026-11-19T09:00:08Z' }),
      ],
    })
    renderAs(<ExchangeRecordCard record={record} />, ['CUSTOMER_QUALITY_ENGINEER'])
    const card = screen.getByRole('article', { name: /Paketabruf/ })
    expect(card).toHaveTextContent('Eingehend bei Zulieferer Tier-1 GmbH')
    for (const label of ['Twin-Suche', 'Twin-Beschreibung', 'Hash-Prüfung', 'Signaturprüfung']) {
      expect(card).toHaveTextContent(label)
    }
    expect(card).toHaveTextContent('10 von 10 Schritten')
    await user.click(within(card).getByRole('button', { name: /Ablauf anzeigen/ }))
    expect(card).toHaveTextContent('Registry (DTR) des Lieferanten')
    expect(within(card).getByText('Shell-ID')).toBeInTheDocument()
    expect(within(card).getByRole('region', { name: 'Digital Twin Registry des Lieferanten' })).toBeInTheDocument()
    expect(within(card).getByRole('region', { name: 'PPF-Paket (Submodel)' })).toHaveTextContent('urn:uuid:s-1')
  })

  it('renders the local twin registration without partner delivery wording', () => {
    const record = exchangeRecord({
      messageType: 'TWIN_REGISTRATION',
      steps: [step({ step: 'TWIN_REGISTRATION' })],
    })
    renderAs(<ExchangeRecordCard record={record} />, ['SUPPLIER_QUALITY_MANAGER'])
    const card = screen.getByRole('article', { name: /Twin-Registrierung/ })
    expect(card).toHaveTextContent('eigene Registry · für Demo-OEM AG')
    expect(card).toHaveTextContent('1 von 1 Schritten')
  })

  it('English labels for new kinds', async () => {
    await i18n.changeLanguage('en')
    renderAs(
      <ExchangeRecordCard
        record={exchangeRecord({
          direction: 'INBOUND',
          messageType: 'PULL_DOCUMENT',
          status: 'IN_PROGRESS',
          steps: [step({ step: 'DOCUMENT_FETCH' })],
        })}
      />,
      ['CUSTOMER_QUALITY_ENGINEER'],
    )
    const card = screen.getByRole('article', { name: /Document pull/ })
    expect(card).toHaveTextContent('Document fetch')
    expect(card).toHaveTextContent('Hash check')
  })
})

describe('<ExchangeModeBadge>', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('de')
  })

  it.each([
    ['PUSH', 'Push', /vollständige Paket als Catena-X-Nachricht/],
    ['PULL', 'Pull', /regelmäßigen Abfrage/],
    ['PUSH_NOTIFY_PULL_PAYLOAD', 'Benachrichtigung + Pull', /benachrichtigt/],
  ] as const)('%s → "%s" with an explaining label', (mode, text, hint) => {
    renderAs(<ExchangeModeBadge mode={mode} />, [])
    const badge = screen.getByRole('button', { name: new RegExp(`Austauschmodus: ${text.replace('+', '\\+')}`) })
    expect(badge).toHaveTextContent(text)
    expect(badge).toHaveAccessibleName(hint)
  })

  it('plain chip inside other controls', () => {
    renderAs(<ExchangeModeBadge mode="PULL" interactive={false} />, [])
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText('Pull')).toBeInTheDocument()
  })
})
