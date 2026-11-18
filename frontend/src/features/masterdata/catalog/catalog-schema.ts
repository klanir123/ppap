import { z } from 'zod'

import { optionalText, requiredText, zCatalogCode, zOptionalJsonObject, zSortOrder } from '@/lib/validation'

import type {
  CatalogElement,
  CatalogElementInput,
  CatalogLevel,
  CatalogLevelInput,
  CatalogReason,
  CatalogReasonInput,
} from '../api/types'

const entryShape = {
  code: zCatalogCode,
  nameDe: requiredText(255),
  nameEn: requiredText(255),
  descriptionDe: optionalText(2000),
  descriptionEn: optionalText(2000),
  sortOrder: zSortOrder,
}

function entryDefaults(
  entry?: {
    code: string
    nameDe: string
    nameEn: string
    descriptionDe?: string
    descriptionEn?: string
    sortOrder: number
  },
  nextSortOrder = 10,
) {
  return {
    code: entry?.code ?? '',
    nameDe: entry?.nameDe ?? '',
    nameEn: entry?.nameEn ?? '',
    descriptionDe: entry?.descriptionDe ?? '',
    descriptionEn: entry?.descriptionEn ?? '',
    sortOrder: entry?.sortOrder ?? nextSortOrder,
  }
}

export const elementSchema = z.object({
  ...entryShape,
  referenceVda: optionalText(255),
  referenceAiag: optionalText(255),
  evidenceType: z.enum(['DOCUMENT', 'VALUES', 'DOCUMENT_AND_VALUES'], 'REQUIRED'),
  valueSchema: zOptionalJsonObject,
})
export type ElementFormValues = z.input<typeof elementSchema>

export function elementDefaults(element: CatalogElement | undefined, nextSortOrder: number): ElementFormValues {
  return {
    ...entryDefaults(element, nextSortOrder),
    referenceVda: element?.referenceVda ?? '',
    referenceAiag: element?.referenceAiag ?? '',
    evidenceType: element?.evidenceType ?? 'DOCUMENT',
    valueSchema: element?.valueSchema ? JSON.stringify(element.valueSchema, null, 2) : '',
  }
}

export function toElementInput(values: z.output<typeof elementSchema>): CatalogElementInput {
  return {
    ...values,
    valueSchema: values.valueSchema ? (JSON.parse(values.valueSchema) as Record<string, unknown>) : undefined,
  }
}

export const levelSchema = z.object({ ...entryShape, scheme: z.enum(['VDA', 'AIAG', 'CUSTOM'], 'REQUIRED') })
export type LevelFormValues = z.input<typeof levelSchema>

export function levelDefaults(level: CatalogLevel | undefined, nextSortOrder: number): LevelFormValues {
  return { ...entryDefaults(level, nextSortOrder), scheme: level?.scheme ?? 'VDA' }
}

/** The matrix is edited separately; updating the master data keeps the existing requirements. */
export function toLevelInput(values: z.output<typeof levelSchema>, existing?: CatalogLevel): CatalogLevelInput {
  return { ...values, requirements: existing?.requirements ?? {} }
}

export const reasonSchema = z.object(entryShape)
export type ReasonFormValues = z.input<typeof reasonSchema>

export function reasonDefaults(reason: CatalogReason | undefined, nextSortOrder: number): ReasonFormValues {
  return entryDefaults(reason, nextSortOrder)
}

export function toReasonInput(values: z.output<typeof reasonSchema>): CatalogReasonInput {
  return values
}

/** Next free sort order (step 10). */
export function nextSortOrder(entries: { sortOrder: number }[] | undefined): number {
  const max = Math.max(0, ...(entries ?? []).map((e) => e.sortOrder))
  return Math.floor(max / 10) * 10 + 10
}
