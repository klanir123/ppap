import { useTranslation } from 'react-i18next'

import type { JsonSchema } from '../api/types'
import type { SchemaError } from './schema'

/** `lowerTolerance` → `Lower tolerance` (last resort for keys without translation or title). */
export function humanize(key: string): string {
  const words = key
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * Labels for schema properties: `x-title-<lng>` / `title` from the schema snapshot (data),
 * then the shared label vocabulary (`approvals:valueFields.<key>`), then the humanised key.
 */
export function useSchemaText() {
  const { t, i18n } = useTranslation('approvals')
  const lng = i18n.resolvedLanguage ?? 'de'
  return {
    label(key: string, schema: JsonSchema): string {
      const localized = schema[`x-title-${lng}`]
      if (typeof localized === 'string') return localized
      if (typeof schema.title === 'string') return schema.title
      return t(`valueFields.${key}` as 'valueFields.characteristic', { defaultValue: humanize(key) })
    },
    description(schema: JsonSchema): string | undefined {
      const localized = schema[`x-description-${lng}`]
      if (typeof localized === 'string') return localized
      return typeof schema.description === 'string' ? schema.description : undefined
    },
    error(error: SchemaError): string {
      const text = t(`schemaForm.errors.${error.code}` as 'schemaForm.errors.REQUIRED', {
        ...error.params,
        defaultValue: error.message ?? error.code,
      })
      // server messages carry the concrete violation (English) – keep it as detail
      return error.message && error.code === 'VALUE_SCHEMA_VIOLATION' ? `${text} (${error.message})` : text
    },
  }
}
