import { Check, Minus, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { JsonSchema, JsonValue } from '../api/types'
import { useSchemaText } from './labels'
import { fieldKind, itemSchema, properties } from './schema'

/** Read-only rendering of element values (review, revision view). */
export function SchemaValueView({
  schema,
  value,
  label,
}: {
  schema: JsonSchema | null | undefined
  value: JsonValue | undefined
  label: string
}) {
  const { t } = useTranslation('approvals')
  if (value === undefined || value === null || (Array.isArray(value) && value.length === 0)) {
    return <p className="text-sm text-muted-foreground">{t('schemaForm.noValues')}</p>
  }
  if (!schema) {
    return (
      <pre className="relative overflow-x-auto rounded-md bg-muted p-3 text-xs">{JSON.stringify(value, null, 2)}</pre>
    )
  }
  return <Node schema={schema} value={value} label={label} />
}

function Node({ schema, value, label }: { schema: JsonSchema; value: JsonValue | undefined; label: string }) {
  const text = useSchemaText()
  const kind = fieldKind(schema)
  if (kind === 'object' && value && typeof value === 'object' && !Array.isArray(value)) {
    return (
      <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
        {properties(schema).map(([key, prop]) => (
          <div key={key} className="min-w-0 space-y-0.5">
            <dt className="text-xs text-muted-foreground">{text.label(key, prop)}</dt>
            <dd className="break-words">
              <Node schema={prop} value={value[key]} label={text.label(key, prop)} />
            </dd>
          </div>
        ))}
      </dl>
    )
  }
  if (kind === 'array' && Array.isArray(value)) {
    const items = itemSchema(schema)
    const columns: [string, JsonSchema][] =
      items && fieldKind(items) === 'object' ? properties(items) : [['', items ?? {}]]
    return (
      <div className="relative overflow-x-auto rounded-md border">
        <table className="w-full text-sm" aria-label={label}>
          <thead className="bg-muted/60 text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-10 px-3 py-2 text-left font-medium">
                #
              </th>
              {columns.map(([key, prop]) => (
                <th key={key || 'value'} scope="col" className="px-3 py-2 text-left font-medium whitespace-nowrap">
                  {key ? text.label(key, prop) : ''}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {value.map((row, i) => (
              <tr key={i} className="border-t">
                <td className="px-3 py-2 text-xs text-muted-foreground tabular">{i + 1}</td>
                {columns.map(([key, prop]) => (
                  <td key={key || 'value'} className="px-3 py-2 whitespace-nowrap">
                    <Node
                      schema={prop}
                      value={key && row && typeof row === 'object' && !Array.isArray(row) ? row[key] : row}
                      label={key}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }
  return <Primitive kind={kind} value={value} />
}

function Primitive({ kind, value }: { kind: string; value: JsonValue | undefined }) {
  const { t, i18n } = useTranslation('approvals')
  if (value === undefined || value === null || value === '') {
    return (
      <span className="text-muted-foreground">
        <Minus aria-hidden="true" className="inline size-3.5" />
        <span className="sr-only">{t('schemaForm.empty')}</span>
      </span>
    )
  }
  if (typeof value === 'boolean') {
    const Icon = value ? Check : X
    return (
      <span className="inline-flex items-center gap-1">
        <Icon aria-hidden="true" className={value ? 'size-3.5 text-(--status-ok-fg)' : 'size-3.5 text-destructive'} />
        {value ? t('schemaForm.yes') : t('schemaForm.no')}
      </span>
    )
  }
  if (typeof value === 'number') {
    return <span className="tabular">{new Intl.NumberFormat(i18n.resolvedLanguage).format(value)}</span>
  }
  if (kind === 'date' && typeof value === 'string') {
    const date = new Date(`${value}T00:00:00`)
    return (
      <time dateTime={value}>
        {Number.isNaN(date.getTime())
          ? value
          : new Intl.DateTimeFormat(i18n.resolvedLanguage, { dateStyle: 'medium' }).format(date)}
      </time>
    )
  }
  if (typeof value === 'object') return <code className="text-xs">{JSON.stringify(value)}</code>
  return <span>{String(value)}</span>
}
