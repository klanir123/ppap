/**
 * Import types for the generic wizard. Structurally compatible with the generated
 * ImportPreviewResponse / ImportResultResponse (narrower enums, nullable mapping targets).
 */
import type { ValidationStatus } from '@/lib/status'

export type ImportEntity = 'parts' | 'partners' | 'sites' | 'relations' | 'part-mappings'
export type ImportAction = 'CREATE' | 'UPDATE' | 'UNCHANGED'
/** column header → target field (null = ignore) */
export type ColumnMapping = Record<string, string | null>

export interface ImportMessage {
  field?: string
  code: string
  message?: string
}

export interface ImportTargetField {
  name: string
  required: boolean
  labelKey?: string
}

export interface ImportRow {
  rowNumber: number
  values: Record<string, unknown>
  status: ValidationStatus
  action?: ImportAction
  messages: ImportMessage[]
}

export interface ImportSummary {
  total: number
  ok: number
  warnings: number
  errors: number
  create: number
  update: number
  unchanged: number
}

export interface ImportPreview {
  entity: string
  fileName?: string
  sheet?: string | null
  delimiter?: string | null
  columns: string[]
  suggestedMapping: ColumnMapping
  targetFields: ImportTargetField[]
  rows: ImportRow[]
  summary: ImportSummary
}

export interface ImportResult {
  entity: string
  created: number
  updated: number
  unchanged: number
  failed: number
  failures: { rowNumber: number; messages: ImportMessage[] }[]
}

/** Callbacks the wizard uses – implemented by `importApi(entity)` or mocks in tests. */
export interface ImportHandlers {
  preview: (file: File, mapping?: ColumnMapping) => Promise<ImportPreview>
  execute: (file: File, mapping: ColumnMapping, skipErrors: boolean) => Promise<ImportResult>
  errorReport: (file: File, mapping: ColumnMapping) => Promise<Blob>
}

export const MAX_IMPORT_BYTES = 10 * 1024 * 1024
export const ACCEPTED_EXTENSIONS = ['.csv', '.xlsx'] as const
