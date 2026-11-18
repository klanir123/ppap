import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, RefreshCw, TriangleAlert } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { FormProvider, useForm, type DefaultValues, type FieldValues, type UseFormReturn } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import type { z } from 'zod'

import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { translateErrorCode, translateFieldError, type MappedProblem } from '@/lib/problem-errors'

import { FormReadOnlyContext } from './form-context'
import { applyProblemToForm } from './server-errors'

type AnySchema = z.ZodType<FieldValues, FieldValues>

export interface FormSheetProps<S extends AnySchema> {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  schema: S
  defaultValues: DefaultValues<z.input<S>>
  /**
   * Remounts the form when it changes (e.g. `${id}:${version}`), so a reload after a
   * version conflict starts from the fresh server state.
   */
  formKey?: string
  /** Throw an `ApiError` to have RFC 9457 field errors mapped onto the form. */
  onSubmit: (values: z.output<S>) => Promise<void>
  submitLabel?: string
  /** Shown on VERSION_CONFLICT / 412: reloads the record (caller updates defaultValues + formKey). */
  onReload?: () => void
  readOnly?: boolean
  children: (form: UseFormReturn<z.input<S>, unknown, z.output<S>>) => ReactNode
}

/**
 * Side drawer with a react-hook-form + zod form.
 * - dirty guard: closing with unsaved changes asks for confirmation
 * - server problem details: `errors[]` are mapped onto fields, the rest shown above the form
 */
export function FormSheet<S extends AnySchema>(props: FormSheetProps<S>) {
  const { open, onOpenChange, title, description, formKey } = props
  const { t } = useTranslation('forms')
  const [dirty, setDirty] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)

  function requestClose(next: boolean) {
    if (next) return onOpenChange(true)
    if (dirty && !props.readOnly) setConfirmOpen(true)
    else onOpenChange(false)
  }

  return (
    <>
      <Sheet open={open} onOpenChange={requestClose}>
        <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-lg">
          <SheetHeader className="border-b px-6 py-4">
            <SheetTitle>{title}</SheetTitle>
            {description ? <SheetDescription>{description}</SheetDescription> : null}
          </SheetHeader>
          {open ? (
            <FormSheetBody
              key={formKey}
              {...props}
              onDirtyChange={setDirty}
              onCancel={() => requestClose(false)}
              onDone={() => {
                setDirty(false)
                onOpenChange(false)
              }}
            />
          ) : null}
        </SheetContent>
      </Sheet>
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t('discardTitle')}
        description={t('discardDescription')}
        confirmLabel={t('discard')}
        cancelLabel={t('keepEditing')}
        destructive
        onConfirm={() => {
          setConfirmOpen(false)
          setDirty(false)
          onOpenChange(false)
        }}
      />
    </>
  )
}

function FormSheetBody<S extends AnySchema>({
  schema,
  defaultValues,
  onSubmit,
  submitLabel,
  onReload,
  readOnly = false,
  children,
  onDirtyChange,
  onCancel,
  onDone,
}: FormSheetProps<S> & { onDirtyChange: (dirty: boolean) => void; onCancel: () => void; onDone: () => void }) {
  const { t } = useTranslation('forms')
  const form = useForm<z.input<S>, unknown, z.output<S>>({
    resolver: zodResolver(schema),
    defaultValues,
    mode: 'onTouched',
  })
  const [problem, setProblem] = useState<MappedProblem | null>(null)
  const { isDirty, isSubmitting } = form.formState

  useEffect(() => onDirtyChange(isDirty), [isDirty, onDirtyChange])

  const submit = form.handleSubmit(async (values) => {
    setProblem(null)
    try {
      await onSubmit(values)
      onDone()
    } catch (error) {
      const mapped = applyProblemToForm(error, Object.keys(defaultValues ?? {}), form.setError)
      if (mapped.formCode || mapped.unmapped.length > 0) setProblem(mapped)
    }
  })

  return (
    <FormProvider {...form}>
      <FormReadOnlyContext value={readOnly}>
        <form onSubmit={(e) => void submit(e)} noValidate className="flex min-h-0 flex-1 flex-col">
          <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
            {problem ? (
              problem.isVersionConflict ? (
                <Alert>
                  <RefreshCw aria-hidden="true" />
                  <AlertTitle>{t('versionConflictTitle')}</AlertTitle>
                  <AlertDescription>
                    <p>{t('versionConflictDescription')}</p>
                    {onReload ? (
                      <Button type="button" size="sm" variant="outline" className="mt-2" onClick={onReload}>
                        <RefreshCw aria-hidden="true" />
                        {t('reload')}
                      </Button>
                    ) : null}
                  </AlertDescription>
                </Alert>
              ) : (
                <Alert variant="destructive" role="alert">
                  <TriangleAlert aria-hidden="true" />
                  <AlertTitle>{t('errorSummary')}</AlertTitle>
                  <AlertDescription>
                    {problem.formCode ? <p>{translateErrorCode(problem.formCode, problem.formMessage)}</p> : null}
                    {problem.unmapped.length > 0 ? (
                      <ul className="list-disc pl-4">
                        {problem.unmapped.map((u, i) => (
                          <li key={`${u.field}-${i}`}>
                            {u.field ? <span className="font-mono">{u.field}: </span> : null}
                            {translateFieldError(u.code, u.field, u.message)}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </AlertDescription>
                </Alert>
              )
            ) : null}
            {children(form)}
          </div>
          <SheetFooter className="flex-row justify-end gap-2 border-t px-6 py-4">
            <Button type="button" variant="outline" onClick={onCancel}>
              {t('cancel')}
            </Button>
            {readOnly ? null : (
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? <Loader2 aria-hidden="true" className="animate-spin" /> : null}
                {isSubmitting ? t('saving') : (submitLabel ?? t('save'))}
              </Button>
            )}
          </SheetFooter>
        </form>
      </FormReadOnlyContext>
    </FormProvider>
  )
}
