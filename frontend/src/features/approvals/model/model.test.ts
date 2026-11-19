import { describe, expect, it } from 'vitest'

import { approvalCase } from '../__tests__/fixtures'
import { availableWorkspace, permittedActions } from './actions'
import { checkDecision, suggestResult } from './decision-rules'
import { elementState, issuesByElement } from './elements'
import { boardStatuses, groupByStatus } from './kanban'
import { caseStatusToken, daysUntil } from './status'

describe('kanban grouping', () => {
  const cases = [
    approvalCase({ id: '1', caseNumber: 'PPF-3', status: 'REQUESTED', dueDate: '2026-12-20' }),
    approvalCase({ id: '2', caseNumber: 'PPF-1', status: 'REQUESTED', dueDate: '2026-12-01', overdue: false }),
    approvalCase({ id: '3', caseNumber: 'PPF-2', status: 'REQUESTED', dueDate: '2026-12-30', overdue: true }),
    approvalCase({ id: '4', caseNumber: 'PPF-4', status: 'IN_REVIEW', dueDate: undefined }),
    approvalCase({ id: '5', caseNumber: 'PPF-5', status: 'CANCELLED' }),
  ]

  it('groups by status in workflow order, overdue first, then by due date', () => {
    const columns = groupByStatus(cases, boardStatuses(undefined))
    expect(columns.map((c) => c.status)).toEqual([
      'DRAFT',
      'REQUESTED',
      'IN_PROGRESS',
      'SUBMITTED',
      'IN_REVIEW',
      'APPROVED',
      'CONDITIONALLY_APPROVED',
      'REJECTED',
    ])
    expect(columns.find((c) => c.status === 'REQUESTED')!.cases.map((c) => c.id)).toEqual(['3', '2', '1'])
    expect(columns.find((c) => c.status === 'IN_REVIEW')!.cases).toHaveLength(1)
    expect(columns.find((c) => c.status === 'DRAFT')!.cases).toEqual([])
  })

  it('hides cancelled cases unless filtered for; status filter limits the columns', () => {
    expect(groupByStatus(cases, boardStatuses(undefined)).some((c) => c.status === 'CANCELLED')).toBe(false)
    const filtered = groupByStatus(cases, boardStatuses(['CANCELLED', 'REQUESTED']))
    expect(filtered.map((c) => c.status)).toEqual(['REQUESTED', 'CANCELLED'])
    expect(filtered[1]!.cases.map((c) => c.id)).toEqual(['5'])
  })
})

describe('allowed actions', () => {
  it('combines server allowedActions with UI permissions and ignores unknown actions', () => {
    const allowed = ['SEND', 'CANCEL', 'START_WORK', 'SOMETHING_NEW']
    expect(permittedActions(allowed, ['CUSTOMER_QUALITY_ENGINEER'])).toEqual(['SEND', 'CANCEL'])
    expect(permittedActions(allowed, ['SUPPLIER_QUALITY_MANAGER'])).toEqual(['CANCEL', 'START_WORK'])
    expect(permittedActions(allowed, ['CONNECTOR_ADMIN'])).toEqual([])
    expect(permittedActions([], ['CUSTOMER_QUALITY_ENGINEER'])).toEqual([])
  })

  it('opens the package workspace for suppliers in progress and the review for customers', () => {
    expect(availableWorkspace({ ownRole: 'SUPPLIER', status: 'IN_PROGRESS' }, ['SUBMIT'])).toBe('package')
    expect(availableWorkspace({ ownRole: 'SUPPLIER', status: 'IN_PROGRESS' }, [])).toBeUndefined()
    expect(availableWorkspace({ ownRole: 'CUSTOMER', status: 'SUBMITTED' }, ['START_REVIEW'])).toBe('review')
    expect(availableWorkspace({ ownRole: 'CUSTOMER', status: 'IN_REVIEW' }, ['DECIDE'])).toBe('review')
    expect(availableWorkspace({ ownRole: 'CUSTOMER', status: 'APPROVED' }, ['DECIDE'])).toBeUndefined()
  })
})

describe('decision rules (client mirror of the server rules)', () => {
  const codes = ['PPF-01', 'PPF-11', 'PPF-14']
  const today = '2026-11-19'

  it('requires a result', () => {
    expect(checkDecision({ conditions: [] }, {}, codes, today).errors).toEqual([{ code: 'RESULT_REQUIRED' }])
  })

  it('REJECTED needs at least one NOT_ACCEPTED element with a comment each', () => {
    const none = checkDecision({ result: 'REJECTED', conditions: [] }, {}, codes, today)
    expect(none.errors).toEqual([{ code: 'REJECTION_NEEDS_NOT_ACCEPTED' }])
    const findings = {
      'PPF-11': { result: 'NOT_ACCEPTED' as const, comment: '' },
      'PPF-14': { result: 'NOT_ACCEPTED' as const, comment: 'Cpk < 1,33' },
    }
    const missing = checkDecision({ result: 'REJECTED', conditions: [] }, findings, codes, today)
    expect(missing.errors).toEqual([{ code: 'REJECTION_REASON_REQUIRED', elementCode: 'PPF-11' }])
    findings['PPF-11'].comment = 'Merkmal 12 außerhalb Toleranz'
    expect(checkDecision({ result: 'REJECTED', conditions: [] }, findings, codes, today).valid).toBe(true)
  })

  it('CONDITIONALLY_APPROVED needs a condition and a future due date', () => {
    const draft = { result: 'CONDITIONALLY_APPROVED' as const, conditions: ['  '] }
    expect(checkDecision(draft, {}, codes, today).errors.map((e) => e.code)).toEqual([
      'CONDITIONS_REQUIRED',
      'CONDITION_DUE_DATE_REQUIRED',
    ])
    expect(
      checkDecision({ ...draft, conditions: ['Nachmessung'], conditionDueDate: today }, {}, codes, today).errors,
    ).toEqual([{ code: 'CONDITION_DUE_DATE_PAST' }])
    expect(
      checkDecision({ ...draft, conditions: ['Nachmessung'], conditionDueDate: '2026-12-31' }, {}, codes, today).valid,
    ).toBe(true)
  })

  it('APPROVED is blocked by NOT_ACCEPTED elements and reports unreviewed ones', () => {
    const findings = { 'PPF-01': { result: 'ACCEPTED' as const }, 'PPF-11': { result: 'NOT_ACCEPTED' as const } }
    const check = checkDecision({ result: 'APPROVED', conditions: [] }, findings, codes, today)
    expect(check.errors).toEqual([{ code: 'ELEMENTS_NOT_ACCEPTED', elementCode: 'PPF-11' }])
    expect(check.unreviewed).toEqual(['PPF-14'])
  })

  it('suggests the overall result once every element has a finding', () => {
    expect(suggestResult([{ result: 'ACCEPTED' }, undefined])).toBeUndefined()
    expect(suggestResult([{ result: 'ACCEPTED' }, { result: 'ACCEPTED' }])).toBe('APPROVED')
    expect(suggestResult([{ result: 'ACCEPTED' }, { result: 'ACCEPTED_WITH_CONDITION' }])).toBe(
      'CONDITIONALLY_APPROVED',
    )
    expect(suggestResult([{ result: 'NOT_ACCEPTED' }, { result: 'ACCEPTED_WITH_CONDITION' }])).toBe('REJECTED')
  })
})

describe('status helpers', () => {
  it('maps API statuses to design tokens', () => {
    expect(caseStatusToken('CONDITIONALLY_APPROVED')).toBe('conditionally-approved')
    expect(caseStatusToken('IN_PROGRESS')).toBe('in-progress')
  })

  it('counts days until the due date (local dates)', () => {
    const now = new Date(2026, 10, 19, 23, 30)
    expect(daysUntil('2026-11-19', now)).toBe(0)
    expect(daysUntil('2026-11-22', now)).toBe(3)
    expect(daysUntil('2026-11-17', now)).toBe(-2)
  })

  it('derives the element state from assessment and issues', () => {
    const element = {
      code: 'PPF-01',
      requirement: 'REQUIRED' as const,
      documents: [],
      selfAssessment: 'OK' as const,
      complete: true,
      issues: [],
    }
    expect(elementState('REQUIRED', undefined, 1)).toBe('issues')
    expect(elementState('ON_REQUEST', undefined, 0)).toBe('optional')
    expect(elementState('REQUIRED', undefined, 0)).toBe('open')
    expect(elementState('REQUIRED', element, 0)).toBe('complete')
    const map = issuesByElement({
      complete: false,
      requiredDone: 0,
      requiredTotal: 2,
      issues: [
        { elementCode: 'A', code: 'X', message: 'X' },
        { elementCode: 'A', code: 'Y', message: 'Y' },
      ],
    })
    expect(map.get('A')).toHaveLength(2)
  })
})
