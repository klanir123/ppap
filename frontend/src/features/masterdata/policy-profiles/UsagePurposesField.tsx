import { Plus, X } from 'lucide-react'
import { useState } from 'react'
import { Controller, useFormContext } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { FieldShell, type BaseFieldProps } from '@/components/forms/FieldShell'
import { useFormReadOnly } from '@/components/forms/form-context'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { translateErrorCode } from '@/lib/problem-errors'
import { cn } from '@/lib/utils'

import { usagePurposeError } from './policy-profile-schema'

/**
 * Usage purposes as removable chips + input (Enter or "Hinzufügen"); each value is validated before it is
 * added (format `cx.quality.base:1`, no duplicates). Value: string[].
 */
export function UsagePurposesField({ suggestions = [], ...base }: BaseFieldProps & { suggestions?: string[] }) {
  const { control: formControl } = useFormContext()
  const readOnly = useFormReadOnly()
  const { t } = useTranslation('masterdata')
  const [draft, setDraft] = useState('')
  const [draftError, setDraftError] = useState<string>()
  return (
    <FieldShell {...base}>
      {(control) => (
        <Controller
          control={formControl}
          name={base.name}
          render={({ field }) => {
            const value: string[] = Array.isArray(field.value) ? (field.value as string[]) : []
            const add = (raw: string) => {
              const error = usagePurposeError(raw, value)
              if (error) {
                setDraftError(error)
                return
              }
              field.onChange([...value, raw.trim()])
              setDraft('')
              setDraftError(undefined)
            }
            const errorId = draftError ? `${control.id}-draft-error` : undefined
            return (
              <div className="grid gap-2">
                <ul
                  className="flex min-h-8 flex-wrap items-center gap-1.5"
                  aria-label={base.label}
                  data-testid="usage-purposes"
                >
                  {value.length === 0 ? (
                    <li className="text-xs text-muted-foreground">{t('policyProfiles.form.noPurposes')}</li>
                  ) : (
                    value.map((purpose) => (
                      <li
                        key={purpose}
                        className="inline-flex h-7 items-center gap-1 rounded-full border border-participant/30 bg-participant-soft pr-1 pl-2.5 font-mono text-xs text-participant-soft-foreground"
                      >
                        {purpose}
                        {readOnly ? null : (
                          <button
                            type="button"
                            onClick={() => field.onChange(value.filter((v) => v !== purpose))}
                            className="flex size-5 items-center justify-center rounded-full hover:bg-background/60 focus-visible:outline-2 focus-visible:outline-ring"
                            aria-label={t('policyProfiles.form.removePurpose', { purpose })}
                          >
                            <X aria-hidden="true" className="size-3" />
                          </button>
                        )}
                      </li>
                    ))
                  )}
                </ul>
                {readOnly ? null : (
                  <div className="flex gap-2">
                    <Input
                      id={control.id}
                      ref={field.ref}
                      value={draft}
                      placeholder="cx.quality.base:1"
                      spellCheck={false}
                      autoComplete="off"
                      aria-invalid={Boolean(draftError) || control['aria-invalid']}
                      aria-describedby={[control['aria-describedby'], errorId].filter(Boolean).join(' ') || undefined}
                      className={cn('font-mono', draftError && 'border-destructive')}
                      onChange={(e) => {
                        setDraft(e.target.value)
                        setDraftError(undefined)
                      }}
                      onBlur={field.onBlur}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          add(draft)
                        }
                      }}
                      list={suggestions.length ? `${control.id}-suggestions` : undefined}
                    />
                    <Button type="button" variant="outline" onClick={() => add(draft)} disabled={!draft.trim()}>
                      <Plus aria-hidden="true" />
                      {t('policyProfiles.form.addPurpose')}
                    </Button>
                    {suggestions.length ? (
                      <datalist id={`${control.id}-suggestions`}>
                        {suggestions.map((s) => (
                          <option key={s} value={s} />
                        ))}
                      </datalist>
                    ) : null}
                  </div>
                )}
                {draftError ? (
                  <p id={errorId} role="alert" className="text-xs font-medium text-destructive">
                    {translateErrorCode(draftError)}
                  </p>
                ) : null}
              </div>
            )
          }}
        />
      )}
    </FieldShell>
  )
}
