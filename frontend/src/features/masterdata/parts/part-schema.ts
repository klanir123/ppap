import { z } from 'zod'

import { optionalText, requiredText, zOptionalCatenaXId, zOptionalPartNumber, zPartNumber } from '@/lib/validation'

import type { Part, PartInput } from '../api/types'

export const partSchema = z.object({
  partNumber: zPartNumber,
  nameDe: requiredText(255),
  nameEn: optionalText(255),
  drawingNumber: zOptionalPartNumber,
  drawingRevision: optionalText(32),
  material: optionalText(255),
  catenaXId: zOptionalCatenaXId,
})
export type PartFormValues = z.input<typeof partSchema>

export function partDefaults(part?: Part): PartFormValues {
  return {
    partNumber: part?.partNumber ?? '',
    nameDe: part?.nameDe ?? '',
    nameEn: part?.nameEn ?? '',
    drawingNumber: part?.drawingNumber ?? '',
    drawingRevision: part?.drawingRevision ?? '',
    material: part?.material ?? '',
    catenaXId: part?.catenaXId ?? '',
  }
}

export function toPartInput(values: z.output<typeof partSchema>): PartInput {
  return values
}
