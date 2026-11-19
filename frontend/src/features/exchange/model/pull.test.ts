import { describe, expect, it } from 'vitest'

import i18n from '@/lib/i18n'

import { exchangeRecord, step } from '../__tests__/fixtures'
import { PULL_STEPS } from '../api/types'
import { deriveDelivery } from './status'
import { buildTimeline, flowTemplate, groupTimeline } from './steps'
import { normalizeTwin, twinVisibility } from './twin'

describe('pull flows (phase 5)', () => {
  it('has an expected flow per record kind, segmented by target', () => {
    const flat = (kind: string, direction: 'INBOUND' | 'OUTBOUND' = 'INBOUND') =>
      flowTemplate({ direction, messageType: kind }).map((s) => `${s.target}:${s.steps.join(',')}`)
    expect(flat('PULL_SUBMISSION')).toEqual([
      'DTR:CATALOG,NEGOTIATION,TRANSFER,EDR,DTR_LOOKUP,SHELL_DESCRIPTOR',
      'SUBMODEL:CATALOG,NEGOTIATION,TRANSFER,EDR,SUBMODEL_FETCH,HASH_CHECK,SIGNATURE_CHECK,PROCESSING',
      'PUBLIC_KEYS:CATALOG,NEGOTIATION,TRANSFER,EDR,KEY_FETCH',
    ])
    expect(flat('PULL_DOCUMENT')[0]).toContain('DOCUMENT:')
    expect(flat('TWIN_REGISTRATION', 'OUTBOUND')).toEqual(['DTR:TWIN_REGISTRATION'])
    expect(flat('PPAP_SUBMISSION_AVAILABLE', 'OUTBOUND')).toEqual([
      'NOTIFICATION:CATALOG,NEGOTIATION,TRANSFER,EDR,DELIVERY',
    ])
  })

  it('keeps repeated EDC steps of different targets apart and groups them', () => {
    const rows = buildTimeline({
      direction: 'INBOUND',
      messageType: 'PULL_SUBMISSION',
      status: 'PROCESSED',
      steps: [
        step({ step: 'CATALOG', target: 'DTR', attempt: 1 }),
        step({ step: 'EDR', target: 'DTR', at: '2026-11-19T09:00:01Z', attempt: 1 }),
        step({ step: 'DTR_LOOKUP', target: 'DTR', at: '2026-11-19T09:00:02Z', attempt: 1 }),
        step({ step: 'SHELL_DESCRIPTOR', target: 'DTR', at: '2026-11-19T09:00:03Z', attempt: 1 }),
        step({
          step: 'CATALOG',
          target: 'SUBMODEL',
          targetRef: 'urn:uuid:s-1',
          at: '2026-11-19T09:00:04Z',
          attempt: 1,
        }),
        step({ step: 'EDR', target: 'SUBMODEL', at: '2026-11-19T09:00:05Z', attempt: 1 }),
        step({ step: 'SUBMODEL_FETCH', target: 'SUBMODEL', at: '2026-11-19T09:00:06Z', attempt: 1, status: 'FAILED' }),
        step({ step: 'SUBMODEL_FETCH', target: 'SUBMODEL', at: '2026-11-19T09:00:16Z', attempt: 2 }),
        step({ step: 'HASH_CHECK', target: 'SUBMODEL', at: '2026-11-19T09:00:17Z', attempt: 2 }),
        step({ step: 'SIGNATURE_CHECK', target: 'SUBMODEL', at: '2026-11-19T09:00:18Z', attempt: 2 }),
        step({ step: 'PROCESSING', target: 'NOTIFICATION', at: '2026-11-19T09:00:19Z', attempt: 2 }),
      ],
    })
    const groups = groupTimeline(rows)
    expect(groups.map((g) => g.target)).toEqual(['DTR', 'SUBMODEL'])
    expect(groups[1]!.targetRef).toBe('urn:uuid:s-1')
    expect(groups[1]!.steps.map((r) => r.step)).toEqual([
      'CATALOG',
      'EDR',
      'SUBMODEL_FETCH',
      'HASH_CHECK',
      'SIGNATURE_CHECK',
      'PROCESSING',
    ])
    const fetch = rows.find((r) => r.step === 'SUBMODEL_FETCH')!
    expect([fetch.state, fetch.attempts]).toEqual(['OK', 2])
    expect(rows.filter((r) => r.step === 'CATALOG').every((r) => r.attempts === 1)).toBe(true)
  })

  it('merges a pull in flight: optional steps (cached EDR) hidden, missing ones pending', () => {
    const rows = buildTimeline({
      direction: 'INBOUND',
      messageType: 'PULL_SUBMISSION',
      status: 'IN_PROGRESS',
      steps: [
        step({ step: 'CATALOG', target: 'DTR', details: { assetId: 'ppap-dtr' } }),
        step({ step: 'EDR', target: 'DTR', at: '2026-11-19T09:00:01Z', details: { cachedEdr: true } }),
        step({ step: 'DTR_LOOKUP', target: 'DTR', at: '2026-11-19T09:00:02Z' }),
      ],
    })
    expect(rows.map((r) => [r.target, r.step, r.state])).toEqual([
      ['DTR', 'CATALOG', 'OK'],
      ['DTR', 'EDR', 'OK'],
      ['DTR', 'DTR_LOOKUP', 'OK'],
      ['DTR', 'SHELL_DESCRIPTOR', 'PENDING'],
      ['SUBMODEL', 'CATALOG', 'PENDING'],
      ['SUBMODEL', 'EDR', 'PENDING'],
      ['SUBMODEL', 'SUBMODEL_FETCH', 'PENDING'],
      ['SUBMODEL', 'HASH_CHECK', 'PENDING'],
      ['SUBMODEL', 'SIGNATURE_CHECK', 'PENDING'],
      ['SUBMODEL', 'PROCESSING', 'PENDING'],
    ])
  })

  it('shows a failed hash check of a document pull and stops there', () => {
    const rows = buildTimeline({
      direction: 'INBOUND',
      messageType: 'PULL_DOCUMENT',
      status: 'FAILED',
      steps: [
        step({ step: 'CATALOG' }),
        step({ step: 'EDR', at: '2026-11-19T09:00:01Z' }),
        step({ step: 'DOCUMENT_FETCH', at: '2026-11-19T09:00:02Z' }),
        step({ step: 'HASH_CHECK', at: '2026-11-19T09:00:03Z', status: 'FAILED' }),
      ],
    })
    expect(rows.map((r) => r.state)).toEqual(['OK', 'OK', 'OK', 'FAILED'])
  })

  it('every new step type has DE and EN label and explanation', () => {
    for (const lng of ['de', 'en']) {
      for (const name of PULL_STEPS) {
        expect(i18n.exists(`exchange:steps.${name}`, { lng })).toBe(true)
        expect(i18n.exists(`exchange:stepHints.${name}`, { lng })).toBe(true)
      }
      for (const kind of ['PPAP_SUBMISSION_AVAILABLE', 'PULL_SUBMISSION', 'PULL_DOCUMENT', 'TWIN_REGISTRATION']) {
        expect(i18n.exists(`exchange:messageType.${kind}`, { lng })).toBe(true)
      }
    }
  })

  it('delivery ignores twin registrations unless asked for', () => {
    const twin = exchangeRecord({ id: 'r-2', messageType: 'TWIN_REGISTRATION', createdAt: '2026-11-19T10:00:00Z' })
    const notification = exchangeRecord({ id: 'r-1', messageType: 'PPAP_STATUS_UPDATE' })
    expect(deriveDelivery([twin, notification])?.record.id).toBe('r-1')
    expect(deriveDelivery([twin, notification], 'TWIN_REGISTRATION')?.record.id).toBe('r-2')
    expect(
      deriveDelivery([twin, notification], ['PPAP_SUBMISSION', 'PPAP_SUBMISSION_AVAILABLE'])?.record,
    ).toBeUndefined()
  })
})

describe('normalizeTwin', () => {
  it('flattens raw AAS 3.0 descriptors and orders identifiers and revisions', () => {
    const twin = normalizeTwin({
      exchangeId: 'x-1',
      caseId: 'c-1',
      registeredAt: '2026-11-19T09:00:00Z',
      updatedAt: '2026-11-19T09:00:00Z',
      shell: {
        id: 'urn:uuid:x-1',
        idShort: 'ppap-PPF-2026-00031',
        specificAssetIds: [
          {
            name: 'ppapExchangeId',
            value: 'x-1',
            externalSubjectId: { type: 'ExternalReference', keys: [{ value: 'BPNL00000003B2OM' }] },
          },
          { name: 'customerPartId', value: 'D10-4711', externalSubjectId: 'BPNL00000003B2OM' },
        ],
        submodelDescriptors: [
          {
            id: 'urn:uuid:s-1',
            semanticId: { keys: [{ value: 'urn:samm:com.msg.ppap:0.1.0#PpapSubmission' }] },
            endpoints: [
              {
                interface: 'SUBMODEL-3.0',
                protocolInformation: {
                  href: 'http://tier1-edc-dp:8081/api/public/x-1/1',
                  subprotocol: 'DSP',
                  subprotocolBody:
                    'id=ppap-submission-BPNL00000003B2OM;dspEndpoint=http://tier1-edc-cp:8084/api/v1/dsp/2025-1',
                },
              },
            ],
          },
          { id: 'urn:uuid:s-2', idShort: 'PpapSubmission-2', semanticId: 'urn:samm:com.msg.ppap:0.1.0#PpapSubmission' },
        ],
      },
    })
    expect(twin.shellId).toBe('urn:uuid:x-1')
    expect(twin.assetIds.map((a) => a.name)).toEqual(['customerPartId', 'ppapExchangeId'])
    expect(twin.submodels.map((s) => s.revision)).toEqual([2, 1])
    expect(twin.submodels[1]).toMatchObject({
      semanticId: 'urn:samm:com.msg.ppap:0.1.0#PpapSubmission',
      subprotocol: 'DSP',
      href: 'http://tier1-edc-dp:8081/api/public/x-1/1',
    })
    expect(twinVisibility(twin)).toEqual({ restricted: ['BPNL00000003B2OM'], hasPublic: false })
  })
})
