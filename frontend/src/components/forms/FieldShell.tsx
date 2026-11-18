import { useId, type ReactNode } from 'react'
import { useFormContext, useFormState } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

import { fieldErrorText } from './server-errors'

export interface FieldControlProps {
  id: string
  'aria-invalid': boolean
  'aria-describedby': string | undefined
  'aria-required': boolean | undefined
}

export interface BaseFieldProps {
  name: string
  label: string
  description?: string
  required?: boolean
  className?: string
}

function getError(errors: Record<string, unknown>, name: string) {
  return name.split('.').reduce<unknown>((acc, key) => (acc as Record<string, unknown> | undefined)?.[key], errors) as
    { type?: string; message?: string } | undefined
}

/** Label, required marker, description and translated error with correct ARIA wiring. */
export function FieldShell({
  name,
  label,
  description,
  required,
  className,
  asFieldset,
  children,
}: BaseFieldProps & { asFieldset?: boolean; children: (control: FieldControlProps) => ReactNode }) {
  const { t } = useTranslation('forms')
  const id = useId()
  const { control } = useFormContext()
  const { errors } = useFormState({ control, name })
  const error = getError(errors, name)
  const errorText = fieldErrorText(error as never)
  const descriptionId = description ? `${id}-description` : undefined
  const errorId = errorText ? `${id}-error` : undefined
  const describedBy = [descriptionId, errorId].filter(Boolean).join(' ') || undefined
  const marker = required ? (
    <span aria-hidden="true" className="text-destructive">
      *
    </span>
  ) : null
  const srRequired = required ? <span className="sr-only"> ({t('required')})</span> : null

  const control_ = {
    id,
    'aria-invalid': Boolean(errorText),
    'aria-describedby': describedBy,
    'aria-required': required || undefined,
  }
  const body = (
    <>
      {children(control_)}
      {description ? (
        <p id={descriptionId} className="text-xs text-muted-foreground">
          {description}
        </p>
      ) : null}
      {errorText ? (
        <p id={errorId} className="text-xs font-medium text-destructive">
          {errorText}
        </p>
      ) : null}
    </>
  )

  if (asFieldset) {
    return (
      <fieldset className={cn('grid gap-2', className)} aria-describedby={describedBy}>
        <legend className="mb-2 flex items-center gap-1 text-sm font-medium">
          {label}
          {marker}
          {srRequired}
        </legend>
        {body}
      </fieldset>
    )
  }
  return (
    <div className={cn('grid gap-2', className)}>
      <Label htmlFor={id} className="gap-1">
        {label}
        {marker}
        {srRequired}
      </Label>
      {body}
    </div>
  )
}
