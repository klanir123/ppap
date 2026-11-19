import { z } from 'zod'

import type { CatalogElement, CatalogLevel } from '@/features/masterdata/api/types'

import type { ApprovalCaseDetail, CreateCaseInput, DocumentDto, Requirement, RequestElementInput } from '../api/types'

export const REQUEST_STEPS = ['part', 'supplier', 'reason', 'elements', 'schedule', 'summary'] as const
export type RequestStep = (typeof REQUEST_STEPS)[number]

export interface ElementChoice {
  code: string
  selected: boolean
  requirement: Requirement
  note: string
  /** Requirement in the level matrix (NOT_REQUIRED = added from the catalogue). */
  levelRequirement: Requirement | 'NOT_REQUIRED'
}

export interface RequestValues {
  partId: string
  relationId: string
  partnerBpnl: string
  reasonCode: string
  levelCode: string
  /** Level the element list was prefilled from (re-prefill when the level changes). */
  elementsLevel: string
  elements: ElementChoice[]
  dueDate: string
  remarks: string
  attachments: DocumentDto[]
}

export const emptyRequest = (): RequestValues => ({
  partId: '',
  relationId: '',
  partnerBpnl: '',
  reasonCode: '',
  levelCode: '',
  elementsLevel: '',
  elements: [],
  dueDate: '',
  remarks: '',
  attachments: [],
})

const required = z.string().min(1, 'REQUIRED')

/** zod messages are error codes (translated via errors:codes / approvals:wizard.errors). */
function stepSchema(step: RequestStep, today: string) {
  switch (step) {
    case 'part':
      return z.object({ partId: required })
    case 'supplier':
      return z.object({ relationId: required })
    case 'reason':
      return z.object({ reasonCode: required, levelCode: required })
    case 'elements':
      return z.object({
        elements: z
          .array(z.object({ selected: z.boolean(), note: z.string().max(2000, 'TOO_LONG') }))
          .refine((list) => list.some((e) => e.selected), 'MIN_ONE_ELEMENT'),
      })
    case 'schedule':
      return z.object({
        dueDate: required
          .regex(/^\d{4}-\d{2}-\d{2}$/, 'INVALID_DATE')
          .refine((d) => d > today, 'DUE_DATE_NOT_IN_FUTURE'),
        remarks: z.string().max(4000, 'TOO_LONG'),
      })
    case 'summary':
      return z.object({})
  }
}

/** Field → error code for one wizard step. Empty object = step valid. */
export function stepErrors(step: RequestStep, values: RequestValues, today: string): Record<string, string> {
  const result = stepSchema(step, today).safeParse(values)
  if (result.success) return {}
  const errors: Record<string, string> = {}
  for (const issue of result.error.issues) {
    const field = String(issue.path[0] ?? step)
    errors[field] ??= issue.message
  }
  return errors
}

/** First invalid step up to (excluding) `target`, or undefined. */
export function firstInvalidStep(values: RequestValues, target: RequestStep, today: string): RequestStep | undefined {
  for (const step of REQUEST_STEPS) {
    if (step === target) return undefined
    if (Object.keys(stepErrors(step, values, today)).length > 0) return step
  }
  return undefined
}

/**
 * Element list for a level: REQUIRED checked, ON_REQUEST offered unchecked, other catalogue elements
 * available (unchecked). Keeps notes/selection of elements that are already in `previous`.
 */
export function prefillElements(
  level: CatalogLevel | undefined,
  catalogue: readonly CatalogElement[],
  previous: readonly ElementChoice[] = [],
): ElementChoice[] {
  const kept = new Map(previous.map((e) => [e.code, e]))
  return [...catalogue]
    .sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code))
    .map((element) => {
      const levelRequirement = level?.requirements[element.code] ?? 'NOT_REQUIRED'
      const old = kept.get(element.code)
      return {
        code: element.code,
        levelRequirement,
        selected: levelRequirement === 'REQUIRED',
        requirement: levelRequirement === 'NOT_REQUIRED' ? 'REQUIRED' : levelRequirement,
        note: old?.note ?? '',
      }
    })
}

export function elementInputs(values: RequestValues): RequestElementInput[] {
  return values.elements
    .filter((e) => e.selected)
    .map((e) => ({ code: e.code, requirement: e.requirement, note: e.note.trim() || undefined }))
}

export function toCreateInput(values: RequestValues): CreateCaseInput {
  return {
    partId: values.partId,
    relationId: values.relationId || undefined,
    reasonCode: values.reasonCode,
    levelCode: values.levelCode,
    dueDate: values.dueDate,
    remarks: values.remarks.trim() || undefined,
    elements: elementInputs(values),
  }
}

/** Prefill from an existing draft (edit mode). */
export function fromDraft(
  c: ApprovalCaseDetail,
  catalogue: readonly CatalogElement[],
  level?: CatalogLevel,
): RequestValues {
  const requested = new Map(c.request.elements.map((e) => [e.code, e]))
  const elements = prefillElements(level, catalogue).map((choice) => {
    const r = requested.get(choice.code)
    return r
      ? { ...choice, selected: true, requirement: r.requirement, note: r.note ?? '' }
      : { ...choice, selected: false }
  })
  return {
    partId: c.part?.id ?? '',
    relationId: c.relationId ?? '',
    partnerBpnl: c.partner.bpnl,
    reasonCode: c.reason.code,
    levelCode: c.level.code,
    elementsLevel: c.level.code,
    elements,
    dueDate: c.dueDate ?? '',
    remarks: c.remarks ?? '',
    attachments: c.request.attachments,
  }
}
