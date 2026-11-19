import type { ExchangeRecord, ExchangeStep } from '../api/types'

export const step = (s: Partial<ExchangeStep> & Pick<ExchangeStep, 'step'>): ExchangeStep => ({
  status: 'OK',
  target: 'NOTIFICATION',
  at: '2026-11-19T09:00:00Z',
  durationMs: 100,
  ...s,
})

export function exchangeRecord(overrides: Partial<ExchangeRecord> = {}): ExchangeRecord {
  return {
    id: 'r-1',
    messageId: 'm-1',
    direction: 'OUTBOUND',
    messageType: 'PPAP_SUBMISSION',
    partner: { bpnl: 'BPNL00000003B2OM', name: 'Demo-OEM AG' },
    status: 'DELIVERED',
    attempts: 1,

    payloadHash: 'sha256:9f2c4e1a7b3d5f60812a4c6e8b0d2f4a6c8e0b2d4f6a8c0e2b4d6f8a0c2e4b6d',
    correlationId: 'PPF-2026-00031/REV-1',
    createdAt: '2026-11-19T09:00:00Z',
    completedAt: '2026-11-19T09:00:05Z',

    steps: [
      step({ step: 'CATALOG', details: { assetId: 'ppap-notification-receive', offerId: 'offer-1' } }),
      step({
        step: 'NEGOTIATION',
        at: '2026-11-19T09:00:01Z',
        details: { negotiationId: 'neg-1', agreementId: 'agr-1' },
      }),
      step({ step: 'TRANSFER', at: '2026-11-19T09:00:02Z', details: { transferProcessId: 'tp-1' } }),
      step({ step: 'EDR', at: '2026-11-19T09:00:03Z', details: { cachedEdr: true } }),
      step({ step: 'DELIVERY', at: '2026-11-19T09:00:04Z', durationMs: 1520, details: { httpStatus: 202 } }),
    ],
    ...overrides,
  }
}
