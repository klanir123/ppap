import { describe, expect, it } from 'vitest'

import type { CatalogElement, CatalogLevel } from '@/features/masterdata/api/types'

import {
  emptyRequest,
  firstInvalidStep,
  prefillElements,
  stepErrors,
  toCreateInput,
  type RequestValues,
} from './request-model'

const today = '2026-11-19'

const element = (code: string, sortOrder: number): CatalogElement => ({
  id: code,
  code,
  nameDe: code,
  nameEn: code,
  evidenceType: 'DOCUMENT',
  sortOrder,
  active: true,
  version: 1,
})
const catalogue = [element('PPF-03', 3), element('PPF-01', 1), element('PPF-02', 2)]
const level: CatalogLevel = {
  id: 'l',
  code: 'VDA-2',
  nameDe: 'Stufe 2',
  nameEn: 'Level 2',
  scheme: 'VDA',
  sortOrder: 20,
  active: true,
  version: 1,
  requirements: { 'PPF-01': 'REQUIRED', 'PPF-02': 'ON_REQUEST' },
}

function filled(): RequestValues {
  return {
    ...emptyRequest(),
    partId: 'p-1',
    relationId: 'r-1',
    partnerBpnl: 'BPNL0000000TIER1',
    reasonCode: 'NEW_PART',
    levelCode: 'VDA-2',
    elementsLevel: 'VDA-2',
    elements: prefillElements(level, catalogue),
    dueDate: '2026-12-15',
  }
}

describe('request wizard step validation', () => {
  it('validates each step independently with error codes', () => {
    const empty = emptyRequest()
    expect(stepErrors('part', empty, today)).toEqual({ partId: 'REQUIRED' })
    expect(stepErrors('supplier', empty, today)).toEqual({ relationId: 'REQUIRED' })
    expect(stepErrors('reason', empty, today)).toEqual({ reasonCode: 'REQUIRED', levelCode: 'REQUIRED' })
    expect(stepErrors('elements', empty, today)).toEqual({ elements: 'MIN_ONE_ELEMENT' })
    expect(stepErrors('schedule', empty, today)).toEqual({ dueDate: 'REQUIRED' })
    expect(stepErrors('summary', empty, today)).toEqual({})
  })

  it('requires a due date in the future', () => {
    expect(stepErrors('schedule', { ...filled(), dueDate: today }, today)).toEqual({
      dueDate: 'DUE_DATE_NOT_IN_FUTURE',
    })
    expect(stepErrors('schedule', { ...filled(), dueDate: '2026-11-20' }, today)).toEqual({})
  })

  it('finds the first invalid step before the summary', () => {
    expect(firstInvalidStep(filled(), 'summary', today)).toBeUndefined()
    expect(firstInvalidStep({ ...filled(), reasonCode: '' }, 'summary', today)).toBe('reason')
    expect(firstInvalidStep({ ...filled(), partId: '', reasonCode: '' }, 'summary', today)).toBe('part')
  })

  it('prefills elements from the level matrix: REQUIRED checked, ON_REQUEST offered, others available', () => {
    const elements = prefillElements(level, catalogue)
    expect(elements.map((e) => [e.code, e.levelRequirement, e.selected])).toEqual([
      ['PPF-01', 'REQUIRED', true],
      ['PPF-02', 'ON_REQUEST', false],
      ['PPF-03', 'NOT_REQUIRED', false],
    ])
  })

  it('builds the create request with selected elements and trimmed notes', () => {
    const values = filled()
    values.elements = values.elements.map((e) =>
      e.code === 'PPF-02' ? { ...e, selected: true, note: '  bitte mit Prüfplan ' } : e,
    )
    expect(toCreateInput(values)).toEqual({
      partId: 'p-1',
      relationId: 'r-1',
      reasonCode: 'NEW_PART',
      levelCode: 'VDA-2',
      dueDate: '2026-12-15',
      remarks: undefined,
      elements: [
        { code: 'PPF-01', requirement: 'REQUIRED', note: undefined },
        { code: 'PPF-02', requirement: 'ON_REQUEST', note: 'bitte mit Prüfplan' },
      ],
    })
  })
})
