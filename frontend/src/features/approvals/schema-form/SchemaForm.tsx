import { Plus, Trash2, TriangleAlert } from 'lucide-react'
import { useId, useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { SegmentedControl } from '@/components/SegmentedControl'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

import type { JsonSchema, JsonValue } from '../api/types'
import { NumberInput } from './NumberInput'
import {
  fieldKind,
  itemSchema,
  joinPointer,
  newRow,
  parsePointer,
  properties,
  renderablePointers,
  requiredSet,
  setIn,
  validate,
  type SchemaError,
} from './schema'
import { useSchemaText } from './labels'

export interface SchemaFormProps {
  schema: JsonSchema
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
  /** Server errors (VALUE_SCHEMA_VIOLATION) at JSON pointers. */
  serverErrors?: SchemaError[]
  /** Show client errors of untouched fields as well (e.g. after a save attempt). */
  showAllErrors?: boolean
  disabled?: boolean
  /** Accessible name (element name). */
  label: string
}

interface Ctx {
  idPrefix: string
  disabled?: boolean
  errorsAt: (pointer: string) => SchemaError[]
  touch: (pointer: string) => void
  update: (pointer: string, value: JsonValue | undefined) => void
}

const NONE = '__none__'

/** Tokens for `setIn`: numeric tokens of arrays become numbers. */
function tokensOf(pointer: string): (string | number)[] {
  return parsePointer(pointer).map((t) => (/^\d+$/.test(t) ? Number(t) : t))
}

const pointerId = (prefix: string, pointer: string) => `${prefix}${pointer.replace(/[^A-Za-z0-9_-]/g, '_') || '_root'}`

/**
 * Form generated from an element's JSON Schema snapshot (object → fields, array of objects → editable table).
 * Controlled: emits the full value on every change. Empty inputs remove the property.
 */
export function SchemaForm({ schema, value, onChange, serverErrors, showAllErrors, disabled, label }: SchemaFormProps) {
  const { t } = useTranslation('approvals')
  const idPrefix = useId().replace(/:/g, '')
  const [touched, setTouched] = useState<ReadonlySet<string>>(new Set())
  const clientErrors = useMemo(() => validate(schema, value), [schema, value])
  const renderable = useMemo(() => renderablePointers(schema, value), [schema, value])

  const visible = useMemo(() => {
    const own = clientErrors.filter((e) => showAllErrors || touched.has(e.pointer) || touchedAncestor(touched, e))
    return [...own, ...(serverErrors ?? [])]
  }, [clientErrors, serverErrors, showAllErrors, touched])

  const ctx: Ctx = {
    idPrefix,
    disabled,
    errorsAt: (pointer) => visible.filter((e) => e.pointer === pointer),
    touch: (pointer) => setTouched((prev) => (prev.has(pointer) ? prev : new Set(prev).add(pointer))),
    update: (pointer, next) => onChange(setIn(value, tokensOf(pointer), next) as JsonValue | undefined),
  }
  const unplaced = (serverErrors ?? []).filter((e) => !renderable.has(e.pointer))
  const kind = fieldKind(schema)

  return (
    <div className="space-y-4" data-testid="schema-form">
      {unplaced.length > 0 ? <ErrorSummary errors={unplaced} /> : null}
      {kind === 'object' ? (
        <ObjectFields schema={schema} value={value} pointer="" ctx={ctx} />
      ) : kind === 'array' ? (
        <ArrayField schema={schema} value={value} pointer="" ctx={ctx} label={label} />
      ) : kind === 'unsupported' ? (
        <p className="text-sm text-muted-foreground">{t('schemaForm.unsupported')}</p>
      ) : (
        <PrimitiveField schema={schema} value={value} pointer="" ctx={ctx} label={label} required={false} />
      )}
    </div>
  )
}

/** Row-level errors become visible once a cell of the row was touched. */
function touchedAncestor(touched: ReadonlySet<string>, error: SchemaError): boolean {
  for (const pointer of touched) if (error.pointer.startsWith(`${pointer}/`)) return true
  return false
}

function ObjectFields({
  schema,
  value,
  pointer,
  ctx,
}: {
  schema: JsonSchema
  value: JsonValue | undefined
  pointer: string
  ctx: Ctx
}) {
  const text = useSchemaText()
  const obj = value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  const required = requiredSet(schema)
  return (
    <div className="grid gap-x-4 gap-y-5 sm:grid-cols-2">
      {properties(schema).map(([key, prop]) => {
        const child = joinPointer(pointer, key)
        const kind = fieldKind(prop)
        const fieldLabel = text.label(key, prop)
        const wide = kind === 'array' || kind === 'object' || (kind === 'string' && (prop.maxLength as number) > 200)
        return (
          <div key={key} className={cn(wide && 'sm:col-span-2')}>
            {kind === 'array' ? (
              <ArrayField schema={prop} value={obj[key]} pointer={child} ctx={ctx} label={fieldLabel} withHeading />
            ) : kind === 'object' ? (
              <fieldset className="space-y-3 rounded-md border p-4">
                <legend className="px-1 text-sm font-medium">{fieldLabel}</legend>
                <ObjectFields schema={prop} value={obj[key]} pointer={child} ctx={ctx} />
              </fieldset>
            ) : (
              <PrimitiveField
                schema={prop}
                value={obj[key]}
                pointer={child}
                ctx={ctx}
                label={fieldLabel}
                required={required.has(key)}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

function FieldErrors({ id, errors }: { id: string; errors: SchemaError[] }) {
  const text = useSchemaText()
  if (errors.length === 0) return null
  return (
    <div id={id} className="space-y-0.5">
      {errors.map((e, i) => (
        <p key={`${e.code}-${i}`} className="text-xs font-medium text-destructive">
          {text.error(e)}
        </p>
      ))}
    </div>
  )
}

function PrimitiveField({
  schema,
  value,
  pointer,
  ctx,
  label,
  required,
}: {
  schema: JsonSchema
  value: JsonValue | undefined
  pointer: string
  ctx: Ctx
  label: string
  required: boolean
}) {
  const { t } = useTranslation(['approvals', 'forms'])
  const text = useSchemaText()
  const id = pointerId(ctx.idPrefix, pointer)
  const errors = ctx.errorsAt(pointer)
  const errorId = `${id}-error`
  const descriptionId = `${id}-description`
  const description = text.description(schema)
  const describedBy = [description ? descriptionId : undefined, errors.length ? errorId : undefined]
    .filter(Boolean)
    .join(' ')
  const kind = fieldKind(schema)
  const labelNode = (
    <>
      {label}
      {required ? (
        <>
          <span aria-hidden="true" className="text-destructive">
            *
          </span>
          <span className="sr-only"> ({t('forms:required')})</span>
        </>
      ) : null}
    </>
  )

  return (
    <div className="grid gap-2">
      {kind === 'boolean' ? (
        <span id={`${id}-label`} className="flex items-center gap-1 text-sm font-medium">
          {labelNode}
        </span>
      ) : (
        <Label htmlFor={id} className="gap-1">
          {labelNode}
        </Label>
      )}
      {kind === 'boolean' ? (
        <SegmentedControl<'true' | 'false'>
          size="sm"
          label={label}
          value={typeof value === 'boolean' ? (String(value) as 'true' | 'false') : undefined}
          onValueChange={(v) => {
            ctx.update(pointer, v === 'true')
            ctx.touch(pointer)
          }}
          options={[
            { value: 'true', label: t('schemaForm.yes') },
            { value: 'false', label: t('schemaForm.no') },
          ]}
          invalid={errors.length > 0}
          describedBy={describedBy || undefined}
          disabled={ctx.disabled}
          className="w-fit"
        />
      ) : (
        <ValueInput
          id={id}
          schema={schema}
          value={value}
          onChange={(v) => ctx.update(pointer, v)}
          onBlur={() => ctx.touch(pointer)}
          invalid={errors.length > 0}
          describedBy={describedBy || undefined}
          required={required}
          disabled={ctx.disabled}
        />
      )}
      {description ? (
        <p id={descriptionId} className="text-xs text-muted-foreground">
          {description}
        </p>
      ) : null}
      <FieldErrors id={errorId} errors={errors} />
    </div>
  )
}

interface ValueInputProps {
  id?: string
  schema: JsonSchema
  value: JsonValue | undefined
  onChange: (value: JsonValue | undefined) => void
  onBlur: () => void
  invalid: boolean
  describedBy?: string
  required?: boolean
  disabled?: boolean
  ariaLabel?: string
  compact?: boolean
}

/** Input for one primitive value (string, date, number, integer, enum; booleans in tables). */
function ValueInput({
  id,
  schema,
  value,
  onChange,
  onBlur,
  invalid,
  describedBy,
  required,
  disabled,
  ariaLabel,
  compact,
}: ValueInputProps) {
  const { t } = useTranslation(['approvals', 'forms'])
  const kind = fieldKind(schema)
  const common = {
    id,
    'aria-invalid': invalid || undefined,
    'aria-describedby': describedBy,
    'aria-required': required || undefined,
    'aria-label': ariaLabel,
    disabled,
  }
  const size = compact
    ? cn('h-8 text-sm', kind === 'string' ? 'min-w-44' : kind === 'enum' ? 'min-w-32' : 'min-w-20')
    : undefined

  switch (kind) {
    case 'number':
    case 'integer':
      return (
        <NumberInput
          {...common}
          integer={kind === 'integer'}
          value={value}
          onValueChange={onChange}
          onBlur={onBlur}
          className={cn(size, 'tabular text-right')}
        />
      )
    case 'boolean':
      return (
        <Checkbox
          id={id}
          aria-label={ariaLabel}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          checked={value === true}
          disabled={disabled}
          onCheckedChange={(checked) => {
            onChange(checked === true)
            onBlur()
          }}
        />
      )
    case 'enum': {
      const options = (schema.enum as JsonValue[]).map((o) => String(o))
      return (
        <Select
          value={value === undefined || value === null ? NONE : String(value)}
          onValueChange={(v) => {
            if (v === NONE) onChange(undefined)
            else onChange((schema.enum as JsonValue[]).find((o) => String(o) === v) ?? v)
            onBlur()
          }}
          disabled={disabled}
        >
          <SelectTrigger
            id={id}
            aria-label={ariaLabel}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            className={cn('w-full', size)}
          >
            <SelectValue placeholder={t('forms:selectPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            {required ? null : <SelectItem value={NONE}>{t('forms:none')}</SelectItem>}
            {options.map((o) => (
              <SelectItem key={o} value={o}>
                {o}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )
    }
    case 'date':
      return (
        <Input
          {...common}
          type="date"
          value={typeof value === 'string' ? value : ''}
          onChange={(e) => onChange(e.target.value || undefined)}
          onBlur={onBlur}
          className={cn(size, 'w-full sm:w-44')}
        />
      )
    case 'string':
      return (
        <Input
          {...common}
          type="text"
          value={typeof value === 'string' ? value : value == null ? '' : String(value)}
          maxLength={typeof schema.maxLength === 'number' ? schema.maxLength : undefined}
          onChange={(e) => onChange(e.target.value === '' ? undefined : e.target.value)}
          onBlur={onBlur}
          className={size}
        />
      )
    default:
      return <span className="text-xs text-muted-foreground">{t('schemaForm.unsupported')}</span>
  }
}

function ArrayField({
  schema,
  value,
  pointer,
  ctx,
  label,
  withHeading,
}: {
  schema: JsonSchema
  value: JsonValue | undefined
  pointer: string
  ctx: Ctx
  label: string
  withHeading?: boolean
}) {
  const { t } = useTranslation('approvals')
  const text = useSchemaText()
  const rows = Array.isArray(value) ? value : []
  const items = itemSchema(schema)
  const objectItems = items && fieldKind(items) === 'object'
  const columns: [string, JsonSchema][] = objectItems ? properties(items) : [['', items ?? {}]]
  const required = objectItems ? requiredSet(items) : new Set<string>()
  const arrayErrors = ctx.errorsAt(pointer)
  const id = pointerId(ctx.idPrefix, pointer)
  const headingId = `${id}-heading`
  const maxItems = typeof schema.maxItems === 'number' ? schema.maxItems : Infinity

  // all cell/row errors, listed below the table and linked to the inputs
  const cellErrors = rows.flatMap((_, i) => {
    const rowPointer = joinPointer(pointer, i)
    const rowErrs = ctx.errorsAt(rowPointer).map((e) => ({ e, row: i, column: undefined as string | undefined }))
    const cells = objectItems
      ? columns.flatMap(([key]) =>
          ctx.errorsAt(joinPointer(rowPointer, key)).map((e) => ({ e, row: i, column: key as string | undefined })),
        )
      : []
    return [...rowErrs, ...cells]
  })

  const addRow = () => {
    ctx.update(pointer, [...rows, newRow(items) ?? (objectItems ? {} : null)] as JsonValue)
  }
  const removeRow = (index: number) => {
    const next = rows.filter((_, i) => i !== index)
    ctx.update(pointer, next.length ? next : undefined)
    ctx.touch(pointer)
  }

  return (
    <div className="space-y-2">
      {withHeading ? (
        <p id={headingId} className="text-sm font-medium">
          {label}
        </p>
      ) : null}
      <div className="relative overflow-x-auto rounded-md border">
        <table
          className="w-full text-sm"
          aria-label={label}
          aria-describedby={arrayErrors.length ? `${id}-error` : undefined}
        >
          <thead className="bg-muted/60 text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-10 px-2 py-2 text-left font-medium">
                <span aria-hidden="true">#</span>
                <span className="sr-only">{t('schemaForm.row')}</span>
              </th>
              {columns.map(([key, prop]) => (
                <th key={key || 'value'} scope="col" className="px-2 py-2 text-left font-medium whitespace-nowrap">
                  {key ? text.label(key, prop) : t('schemaForm.value')}
                  {required.has(key) ? (
                    <span aria-hidden="true" className="ml-0.5 text-destructive">
                      *
                    </span>
                  ) : null}
                </th>
              ))}
              <th scope="col" className="w-10 px-2 py-2">
                <span className="sr-only">{t('schemaForm.rowActions')}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={columns.length + 2} className="px-3 py-6 text-center text-sm text-muted-foreground">
                  {t('schemaForm.noRows')}
                </td>
              </tr>
            ) : (
              rows.map((row, i) => {
                const rowPointer = joinPointer(pointer, i)
                const rowObj = row && typeof row === 'object' && !Array.isArray(row) ? row : {}
                return (
                  <tr key={i} className="border-t align-middle">
                    <td className="px-2 py-1.5 text-xs text-muted-foreground tabular">{i + 1}</td>
                    {columns.map(([key, prop]) => {
                      const cellPointer = key ? joinPointer(rowPointer, key) : rowPointer
                      const errs = ctx.errorsAt(cellPointer)
                      const columnLabel = key ? text.label(key, prop) : t('schemaForm.value')
                      return (
                        <td key={key || 'value'} className="px-1.5 py-1.5">
                          <ValueInput
                            id={pointerId(ctx.idPrefix, cellPointer)}
                            compact
                            schema={prop}
                            value={key ? rowObj[key] : row}
                            onChange={(v) => ctx.update(cellPointer, v)}
                            onBlur={() => ctx.touch(cellPointer)}
                            invalid={errs.length > 0}
                            describedBy={errs.length ? `${id}-cell-errors` : undefined}
                            required={required.has(key)}
                            disabled={ctx.disabled}
                            ariaLabel={t('schemaForm.cellLabel', { column: columnLabel, row: i + 1 })}
                          />
                        </td>
                      )
                    })}
                    <td className="px-1.5 py-1.5 text-right">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        disabled={ctx.disabled}
                        aria-label={t('schemaForm.removeRow', { row: i + 1 })}
                        onClick={() => removeRow(i)}
                      >
                        <Trash2 aria-hidden="true" />
                      </Button>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={addRow}
          disabled={ctx.disabled || rows.length >= maxItems}
        >
          <Plus aria-hidden="true" />
          {t('schemaForm.addRow')}
        </Button>
        <span className="text-xs text-muted-foreground tabular">
          {t('schemaForm.rowCount', { count: rows.length })}
        </span>
      </div>
      <FieldErrors id={`${id}-error`} errors={arrayErrors} />
      {cellErrors.length > 0 ? (
        <ul id={`${id}-cell-errors`} className="space-y-0.5">
          {cellErrors.map(({ e, row, column }, index) => (
            <li key={index} className="text-xs font-medium text-destructive">
              {t('schemaForm.cellError', {
                row: row + 1,
                column: column ? text.label(column, columns.find(([k]) => k === column)?.[1] ?? {}) : '',
                message: text.error(e),
              })}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function ErrorSummary({ errors }: { errors: SchemaError[] }): ReactNode {
  const text = useSchemaText()
  return (
    <div role="alert" className="flex gap-2 rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm">
      <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-destructive" />
      <ul className="space-y-0.5">
        {errors.map((e, i) => (
          <li key={i} className="text-destructive">
            {e.pointer ? <code className="mr-1 font-mono text-xs">{e.pointer}</code> : null}
            {text.error(e)}
          </li>
        ))}
      </ul>
    </div>
  )
}
