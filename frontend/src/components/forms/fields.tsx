import { Braces, CheckCircle2, TriangleAlert } from 'lucide-react'
import { Controller, useFormContext } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'

import { FieldShell, type BaseFieldProps } from './FieldShell'
import { useFormReadOnly } from './form-context'

export interface Option {
  value: string
  label: string
  description?: string
}

interface TextFieldProps extends BaseFieldProps {
  type?: 'text' | 'email' | 'url' | 'number'
  placeholder?: string
  mono?: boolean
  /** Field is the business key: editable on create only. */
  disabled?: boolean
  autoComplete?: string
  /** Upper-case input while typing (BPNL, BPNS, codes). */
  uppercase?: boolean
}

export function TextField({
  type = 'text',
  placeholder,
  mono,
  disabled,
  autoComplete,
  uppercase,
  ...base
}: TextFieldProps) {
  const { register } = useFormContext()
  const readOnly = useFormReadOnly()
  const registration = register(base.name, {
    valueAsNumber: type === 'number',
    setValueAs: uppercase ? (v: unknown) => (typeof v === 'string' ? v.toUpperCase() : v) : undefined,
  })
  return (
    <FieldShell {...base}>
      {(control) => (
        <Input
          {...control}
          {...registration}
          type={type}
          placeholder={placeholder}
          disabled={disabled || readOnly}
          autoComplete={autoComplete ?? 'off'}
          spellCheck={mono ? false : undefined}
          className={cn(mono && 'font-mono', uppercase && 'uppercase placeholder:normal-case')}
        />
      )}
    </FieldShell>
  )
}

export function TextareaField({
  rows = 3,
  placeholder,
  ...base
}: BaseFieldProps & { rows?: number; placeholder?: string }) {
  const { register } = useFormContext()
  const readOnly = useFormReadOnly()
  return (
    <FieldShell {...base}>
      {(control) => (
        <Textarea {...control} {...register(base.name)} rows={rows} placeholder={placeholder} disabled={readOnly} />
      )}
    </FieldShell>
  )
}

const NONE = '__none__'

export function SelectField({
  options,
  placeholder,
  allowEmpty,
  disabled,
  ...base
}: BaseFieldProps & { options: Option[]; placeholder?: string; allowEmpty?: boolean; disabled?: boolean }) {
  const { control: formControl } = useFormContext()
  const readOnly = useFormReadOnly()
  const { t } = useTranslation('forms')
  return (
    <FieldShell {...base}>
      {(control) => (
        <Controller
          control={formControl}
          name={base.name}
          render={({ field }) => (
            <Select
              value={field.value ? String(field.value) : allowEmpty ? NONE : undefined}
              onValueChange={(v) => field.onChange(v === NONE ? '' : v)}
              disabled={disabled || readOnly}
            >
              <SelectTrigger {...control} ref={field.ref} onBlur={field.onBlur} className="w-full">
                <SelectValue placeholder={placeholder ?? t('selectPlaceholder')} />
              </SelectTrigger>
              <SelectContent>
                {allowEmpty ? <SelectItem value={NONE}>{t('none')}</SelectItem> : null}
                {options.map((o) => (
                  <SelectItem key={o.value} value={o.value}>
                    {o.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      )}
    </FieldShell>
  )
}

/** Multi-select as a checkbox group (e.g. partner roles). Value: string[]. */
export function CheckboxGroupField({ options, ...base }: BaseFieldProps & { options: Option[] }) {
  const { control: formControl } = useFormContext()
  const readOnly = useFormReadOnly()
  return (
    <FieldShell {...base} asFieldset>
      {(control) => (
        <Controller
          control={formControl}
          name={base.name}
          render={({ field }) => {
            const value: string[] = Array.isArray(field.value) ? (field.value as string[]) : []
            return (
              <div className="flex flex-wrap gap-2">
                {options.map((option, index) => {
                  const optionId = `${control.id}-${option.value}`
                  const checked = value.includes(option.value)
                  return (
                    <Label
                      key={option.value}
                      htmlFor={optionId}
                      className={cn(
                        'flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm font-normal has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring',
                        checked && 'border-participant/50 bg-participant-soft text-participant-soft-foreground',
                        readOnly && 'cursor-not-allowed opacity-60',
                      )}
                    >
                      <Checkbox
                        id={optionId}
                        ref={index === 0 ? field.ref : undefined}
                        checked={checked}
                        disabled={readOnly}
                        aria-describedby={control['aria-describedby']}
                        onCheckedChange={(next) =>
                          field.onChange(next ? [...value, option.value] : value.filter((v) => v !== option.value))
                        }
                        onBlur={field.onBlur}
                      />
                      {option.label}
                    </Label>
                  )
                })}
              </div>
            )
          }}
        />
      )}
    </FieldShell>
  )
}

function jsonError(text: string): string | undefined {
  if (!text.trim()) return undefined
  try {
    const parsed: unknown = JSON.parse(text)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return 'object expected'
    return undefined
  } catch (e) {
    return e instanceof Error ? e.message : String(e)
  }
}

/** JSON editor (plain textarea) with live parse feedback and pretty-print. Value: string. */
export function JsonField({ rows = 10, ...base }: BaseFieldProps & { rows?: number }) {
  const { control: formControl } = useFormContext()
  const readOnly = useFormReadOnly()
  const { t } = useTranslation('forms')
  return (
    <FieldShell {...base}>
      {(control) => (
        <Controller
          control={formControl}
          name={base.name}
          render={({ field }) => {
            const text = typeof field.value === 'string' ? field.value : ''
            const parseError = jsonError(text)
            return (
              <div className="grid gap-2">
                <Textarea
                  {...control}
                  ref={field.ref}
                  value={text}
                  onChange={(e) => field.onChange(e.target.value)}
                  onBlur={field.onBlur}
                  rows={rows}
                  spellCheck={false}
                  disabled={readOnly}
                  className="font-mono text-xs leading-relaxed"
                />
                <div className="flex items-center justify-between gap-2 text-xs">
                  <span
                    aria-live="polite"
                    className={cn(
                      'flex items-center gap-1.5',
                      parseError ? 'text-destructive' : 'text-muted-foreground',
                    )}
                  >
                    {text.trim() ? (
                      parseError ? (
                        <>
                          <TriangleAlert aria-hidden="true" className="size-3.5" />
                          {t('json.invalid', { message: parseError })}
                        </>
                      ) : (
                        <>
                          <CheckCircle2 aria-hidden="true" className="size-3.5" />
                          {t('json.valid')}
                        </>
                      )
                    ) : null}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="xs"
                    disabled={readOnly || !text.trim() || Boolean(parseError)}
                    onClick={() => field.onChange(JSON.stringify(JSON.parse(text), null, 2))}
                  >
                    <Braces aria-hidden="true" />
                    {t('json.format')}
                  </Button>
                </div>
              </div>
            )
          }}
        />
      )}
    </FieldShell>
  )
}
