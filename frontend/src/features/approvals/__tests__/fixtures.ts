import type { ApprovalCase, ApprovalCaseDetail } from '../api/types'

export function approvalCase(overrides: Partial<ApprovalCase> = {}): ApprovalCase {
  return {
    id: 'c-1',
    caseNumber: 'PPF-2026-00017',
    exchangeId: '6d1f0d7e-1111-4c1c-9a55-000000000017',
    ownRole: 'CUSTOMER',
    source: 'LOCAL',
    status: 'DRAFT',
    partner: { bpnl: 'BPNL0000000TIER1', name: 'Zulieferer Tier-1 GmbH' },
    part: { id: 'p-1', partNumber: 'D10-4711', nameDe: 'Halter vorne', nameEn: 'Front bracket' },
    reason: { code: 'NEW_PART', nameDe: 'Neuteil', nameEn: 'New part' },
    level: { code: 'VDA-2', nameDe: 'VDA Stufe 2', nameEn: 'VDA level 2' },
    dueDate: '2026-12-15',
    overdue: false,
    allowedActions: [],
    version: 3,
    ...overrides,
  }
}

export function caseDetail(overrides: Partial<ApprovalCaseDetail> = {}): ApprovalCaseDetail {
  return {
    ...approvalCase(),
    request: { elements: [], attachments: [] },
    reviewFindings: [],
    submissions: [],
    timeline: [],
    childCases: [],
    ...overrides,
  }
}
