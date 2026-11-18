import i18n from '@/lib/i18n'

import type { ColumnMapping, ImportEntity, ImportTargetField } from './types'
/** Required target fields not assigned to any column. */
export function missingRequiredFields(mapping: ColumnMapping, targetFields: ImportTargetField[]): ImportTargetField[] {
  const assigned = new Set(Object.values(mapping).filter(Boolean))
  return targetFields.filter((f) => f.required && !assigned.has(f.name))
}

/** Assigns `field` to `column`; a field can only be mapped once (previous column is cleared). */
export function assignField(mapping: ColumnMapping, column: string, field: string | null): ColumnMapping {
  const next: ColumnMapping = {}
  for (const [col, target] of Object.entries(mapping)) {
    next[col] = field !== null && target === field && col !== column ? null : target
  }
  next[column] = field
  return next
}

export function sameMapping(a: ColumnMapping, b: ColumnMapping): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)])
  return [...keys].every((k) => (a[k] ?? null) === (b[k] ?? null))
}

/** `masterdata.part-mappings.partNumber` → namespace `masterdata`, key `partMappings.partNumber`. */
export function labelKeyToI18n(labelKey: string): { ns: string; key: string } {
  const [ns = 'masterdata', ...rest] = labelKey.split('.')
  const key = rest.map((segment) => segment.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())).join('.')
  return { ns, key }
}

/** Label for a target field: backend `labelKey` or `masterdata.<entity>.<field>`. */
export function targetFieldLabel(entity: ImportEntity, field: Pick<ImportTargetField, 'name' | 'labelKey'>): string {
  const { ns, key } = labelKeyToI18n(field.labelKey ?? `masterdata.${entity}.${field.name}`)
  return i18n.t(key as 'parts.partNumber', { ns: ns as 'masterdata', defaultValue: field.name })
}
