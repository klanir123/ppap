import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { renderInRouter } from '@/test/router'

import { caseDetail } from '../__tests__/fixtures'
import type { Submission, SubSupplierCandidate, SubSupplierReference } from '../api/types'
import { canRequestSubSupplier, hasSubSupplierPending, isReferenceable, referencedCandidateIds } from './cascade-model'
import { ParentCaseLink, SubSuppliersPanel } from './CaseRelations'
import { SubSupplierReferenceList } from './SubSupplierReferenceList'
import { SubSupplierReferencesSection } from './SubSupplierReferencesSection'

const TIER1 = ['SUPPLIER_QUALITY_MANAGER', 'CUSTOMER_QUALITY_ENGINEER']

const candidate = (over: Partial<SubSupplierCandidate> = {}): SubSupplierCandidate => ({
  caseId: 'child-1',
  caseNumber: 'PPF-2026-00012',
  exchangeId: 'ex-child-1',
  subSupplier: { bpnl: 'BPNL000000TIER02', name: 'Demo-Tier2 KG' },
  partNumber: 'T1-520000-00',
  partName: 'Rastnase',
  revision: 1,
  status: 'APPROVED',
  decisionResult: 'APPROVED',
  decidedAt: '2026-11-18T10:00:00Z',
  decisionHash: 'sha256:aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff00112233',
  referenceable: true,
  ...over,
})

const reference: SubSupplierReference = {
  subSupplierBpnl: 'BPNL000000TIER02',
  subSupplierName: 'Demo-Tier2 KG',
  exchangeId: 'ex-child-1',
  caseNumber: 'PPF-2026-00012',
  partNumber: 'T1-520000-00',
  partName: 'Rastnase',
  supplierPartNumber: 'T2-77',
  revision: 1,
  decisionResult: 'APPROVED',
  decidedAt: '2026-11-18T10:00:00Z',
  decisionHash: 'sha256:aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff00112233',
}

const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })

afterEach(() => vi.unstubAllGlobals())

describe('cascade model', () => {
  it('offers the sub-supplier request only when the server allows it and the user has both quality roles', () => {
    const c = { ownRole: 'SUPPLIER' as const, status: 'IN_PROGRESS' as const, allowedActions: ['REQUEST_SUB_SUPPLIER'] }
    expect(canRequestSubSupplier(c as never, TIER1)).toBe(true)
    expect(canRequestSubSupplier(c as never, ['SUPPLIER_QUALITY_MANAGER'])).toBe(false)
    expect(canRequestSubSupplier({ ...c, allowedActions: [] } as never, TIER1)).toBe(false)
    expect(canRequestSubSupplier({ ...c, status: 'SUBMITTED' } as never, TIER1)).toBe(false)
  })

  it('only approved / conditionally approved candidates are referenceable (AK-09-1)', () => {
    expect(isReferenceable(candidate())).toBe(true)
    expect(isReferenceable(candidate({ decisionResult: 'CONDITIONALLY_APPROVED' }))).toBe(true)
    expect(isReferenceable(candidate({ decisionResult: 'REJECTED', status: 'REJECTED', referenceable: false }))).toBe(
      false,
    )
    expect(isReferenceable(candidate({ referenceable: false }))).toBe(false)
  })

  it('maps draft references to candidate ids (child id, exchange id or case number)', () => {
    const list = [candidate(), candidate({ caseId: 'child-2', exchangeId: 'ex-2', caseNumber: 'PPF-2026-00013' })]
    expect(referencedCandidateIds([reference], list)).toEqual(['child-1'])
    expect(referencedCandidateIds([{ ...reference, exchangeId: 'x', caseNumber: 'PPF-2026-00013' }], list)).toEqual([
      'child-2',
    ])
    expect(referencedCandidateIds(null, list)).toEqual([])
  })

  it('reads SUB_SUPPLIER_PENDING from the completeness hints', () => {
    const base = { complete: true, issues: [], hints: [], requiredDone: 1, requiredTotal: 1 }
    expect(
      hasSubSupplierPending({ ...base, hints: [{ code: 'SUB_SUPPLIER_PENDING', elementCode: '', message: '' }] }),
    ).toBe(true)
    expect(hasSubSupplierPending(base)).toBe(false)
  })
})

describe('case relations', () => {
  it('links a child case to its parent', async () => {
    renderInRouter(
      <ParentCaseLink
        approvalCase={caseDetail({
          parentCase: {
            id: 'p-1',
            caseNumber: 'PPF-2026-00009',
            partner: { bpnl: 'BPNL00000000OEM1', name: 'Demo-OEM AG' },
          },
        })}
      />,
    )
    const link = await screen.findByRole('link', { name: 'PPF-2026-00009' })
    expect(link).toHaveAttribute('href', '/approvals/p-1')
    expect(screen.getByText('Kunde: Demo-OEM AG')).toBeInTheDocument()
  })

  it('lists child cases with status and marks open ones as blocking', async () => {
    renderInRouter(
      <SubSuppliersPanel
        approvalCase={caseDetail({
          ownRole: 'SUPPLIER',
          status: 'IN_PROGRESS',
          allowedActions: ['REQUEST_SUB_SUPPLIER'],
          childCases: [
            {
              id: 'k-1',
              caseNumber: 'PPF-2026-00012',
              partner: { bpnl: 'B2', name: 'Demo-Tier2 KG' },
              partNumber: 'T1-5',
              status: 'IN_PROGRESS',
            },
            {
              id: 'k-2',
              caseNumber: 'PPF-2026-00013',
              partner: { bpnl: 'B2', name: 'Demo-Tier2 KG' },
              status: 'APPROVED',
              lastDecisionResult: 'APPROVED',
            },
          ],
        })}
      />,
      TIER1,
    )
    const panel = await screen.findByTestId('sub-suppliers-panel')
    expect(within(panel).getByText('1 Unterlieferanten-Vorgang noch offen')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: /PPF-2026-00012/ })).toHaveTextContent('Noch nicht entschieden')
    expect(within(panel).getByRole('link', { name: /PPF-2026-00013/ })).toHaveTextContent('Freigegeben')
    expect(within(panel).getByRole('link', { name: 'Unterlieferanten-Vorlage anfordern' })).toHaveAttribute(
      'href',
      '/approvals/new?parentId=c-1',
    )
  })
})

describe('<SubSupplierReferencesSection>', () => {
  const draft = {
    revision: 1,
    status: 'DRAFT',
    elements: [],
    completeness: {
      complete: true,
      issues: [],
      hints: [{ code: 'SUB_SUPPLIER_PENDING', message: '' }],
      requiredDone: 0,
      requiredTotal: 0,
    },
    subSupplierReferences: [],
  } as unknown as Submission

  it('saves the selected decided children and keeps rejected ones informational', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        json([
          candidate(),
          candidate({
            caseId: 'child-r',
            caseNumber: 'PPF-2026-00014',
            decisionResult: 'REJECTED',
            status: 'REJECTED',
            referenceable: false,
          }),
        ]),
      ),
    )
    const save = vi.fn(async () => draft)
    renderInRouter(
      <SubSupplierReferencesSection
        approvalCase={caseDetail({
          id: 'parent',
          childCases: [{ id: 'child-1', caseNumber: 'x', partner: { bpnl: 'b', name: 'B' }, status: 'APPROVED' }],
        })}
        draft={draft}
        saver={{ saveSubSupplierReferences: save, status: {} }}
      />,
      TIER1,
    )
    const boxes = await screen.findAllByRole('checkbox')
    expect(boxes).toHaveLength(2)
    expect(boxes[1]).toBeDisabled()
    expect(screen.getByText(/nur zur Information/)).toBeInTheDocument()
    expect(screen.getByTestId('sub-supplier-pending')).toBeInTheDocument()
    await userEvent.click(boxes[0]!)
    expect(save).toHaveBeenCalledWith(['child-1'])
    expect(screen.getByText('1 Freigabe referenziert')).toBeInTheDocument()
    expect(screen.getByText('Dokumente von Unterlieferanten werden nicht weitergegeben.')).toBeInTheDocument()
  })
})

describe('<SubSupplierReferenceList>', () => {
  it('shows supplier, sub-part, result, date and hash with the no-documents note', () => {
    renderInRouter(<SubSupplierReferenceList references={[reference]} />)
    return screen.findByTestId('sub-supplier-reference').then((item) => {
      expect(item).toHaveTextContent('Demo-Tier2 KG')
      expect(item).toHaveTextContent('T1-520000-00')
      expect(item).toHaveTextContent('Freigegeben')
      expect(item).toHaveTextContent('aa11bb22cc33')
      expect(screen.getByTestId('sub-supplier-no-documents')).toHaveTextContent(/nicht weitergegeben/)
    })
  })

  it('renders nothing without references', () => {
    const { container } = renderInRouter(<SubSupplierReferenceList references={[]} />)
    expect(container.querySelector('[data-testid="sub-supplier-reference-list"]')).toBeNull()
  })
})

describe('actorDisplay', () => {
  it('shows the partner / user name and keeps the technical actor as title', async () => {
    const { Timeline } = await import('../components/Timeline')
    renderInRouter(
      <Timeline
        entries={[
          {
            at: '2026-11-20T10:00:00Z',
            action: 'RECEIVE_SUBMISSION',
            actor: 'partner:BPNL000000TIER02',
            actorDisplay: 'Demo-Tier2 KG',
            from: 'IN_PROGRESS',
            to: 'SUBMITTED',
          },
          {
            at: '2026-11-19T10:00:00Z',
            action: 'SEND',
            actor: 'tier1.quality',
            actorDisplay: 'tier1.quality',
            to: 'REQUESTED',
          },
        ]}
      />,
    )
    const actors = await screen.findAllByTestId('actor')
    expect(actors[0]).toHaveTextContent('Demo-Tier2 KG')
    expect(actors[0]).toHaveAttribute('title', 'partner:BPNL000000TIER02')
    expect(actors[1]).toHaveTextContent('tier1.quality')
    expect(actors[1]).not.toHaveAttribute('title')
  })
})
