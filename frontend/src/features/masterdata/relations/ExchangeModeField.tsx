import { ArrowDownToLine, ArrowUpFromLine, BellRing, Info, type LucideIcon } from 'lucide-react'
import { RadioGroup as RadioGroupPrimitive } from 'radix-ui'
import { useId } from 'react'
import { Controller, useFormContext, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { FieldShell } from '@/components/forms/FieldShell'
import { useFormReadOnly } from '@/components/forms/form-context'
import { cn } from '@/lib/utils'

import { EXCHANGE_MODES, type ExchangeMode } from '../api/types'

const RECOMMENDED_EXCHANGE_MODE: ExchangeMode = 'PUSH_NOTIFY_PULL_PAYLOAD'

const ICONS: Record<ExchangeMode, LucideIcon> = {
  PUSH_NOTIFY_PULL_PAYLOAD: BellRing,
  PUSH: ArrowUpFromLine,
  PULL: ArrowDownToLine,
}

const FACTS = ['storage', 'timing', 'pattern'] as const

/**
 * Exchange mode as explained choice cards (radio group: arrow keys, space): who stores the package,
 * when the customer gets it and which Catena-X pattern is used. Recommended: notification + pull.
 */
export function ExchangeModeField({ name = 'exchangeMode' }: { name?: string }) {
  const { t } = useTranslation('masterdata')
  const { control } = useFormContext()
  const readOnly = useFormReadOnly()
  const partnerRole = useWatch({ control, name: 'partnerRole' }) as string | undefined
  const idPrefix = useId()

  return (
    <FieldShell name={name} label={t('relations.exchangeMode')} required asFieldset>
      {(aria) => (
        <div className="space-y-3">
          <Controller
            control={control}
            name={name}
            render={({ field }) => (
              <RadioGroupPrimitive.Root
                ref={field.ref}
                value={field.value as string}
                onValueChange={field.onChange}
                onBlur={field.onBlur}
                disabled={readOnly}
                aria-label={t('relations.exchangeMode')}
                aria-invalid={aria['aria-invalid'] || undefined}
                aria-describedby={aria['aria-describedby']}
                className="grid gap-2"
              >
                {EXCHANGE_MODES.map((mode) => {
                  const Icon = ICONS[mode]
                  const titleId = `${idPrefix}-${mode}-title`
                  const detailsId = `${idPrefix}-${mode}-details`
                  return (
                    <RadioGroupPrimitive.Item
                      key={mode}
                      value={mode}
                      data-mode={mode}
                      aria-labelledby={titleId}
                      aria-describedby={detailsId}
                      className={cn(
                        'group flex w-full items-start gap-3 rounded-lg border bg-card px-3.5 py-3 text-left transition-colors outline-none',
                        'hover:bg-muted/40 focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-70',
                        'data-[state=checked]:border-participant data-[state=checked]:bg-participant-soft/40 data-[state=checked]:ring-1 data-[state=checked]:ring-participant/40',
                      )}
                    >
                      <span
                        aria-hidden="true"
                        className="mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full border group-data-[state=checked]:border-participant"
                      >
                        <span className="size-2 rounded-full bg-participant opacity-0 group-data-[state=checked]:opacity-100" />
                      </span>
                      <span className="min-w-0 flex-1 space-y-2">
                        <span id={titleId} className="flex flex-wrap items-center gap-x-2 gap-y-1">
                          <span className="inline-flex items-start gap-2 text-sm font-medium">
                            <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                            {t(`relations.exchangeModeValues.${mode}`)}
                          </span>
                          {mode === RECOMMENDED_EXCHANGE_MODE ? (
                            <span className="rounded-full border border-participant/40 bg-participant-soft px-2 py-px text-[11px] font-medium text-participant-soft-foreground">
                              {t('relations.modes.recommended')}
                            </span>
                          ) : null}
                        </span>
                        <span id={detailsId} className="block space-y-2">
                          <span className="block text-xs text-muted-foreground">
                            {t(`relations.modes.${mode}.summary`)}
                          </span>
                          <span className="grid grid-cols-1 gap-x-3 gap-y-1 text-xs sm:grid-cols-[7.5rem_minmax(0,1fr)]">
                            {FACTS.map((fact) => (
                              <span key={fact} className="contents">
                                <span className="text-muted-foreground">{t(`relations.modes.facts.${fact}`)}</span>
                                <span className="mb-1 sm:mb-0">{t(`relations.modes.${mode}.${fact}`)}</span>
                              </span>
                            ))}
                          </span>
                        </span>
                      </span>
                    </RadioGroupPrimitive.Item>
                  )
                })}
              </RadioGroupPrimitive.Root>
            )}
          />
          <div className="flex gap-2 rounded-md border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
            <Info aria-hidden="true" className="mt-px size-3.5 shrink-0" />
            <div className="space-y-1">
              <p className="font-medium text-foreground">{t('relations.modes.appliesNext')}</p>
              <p>{t('relations.modes.documents')}</p>
              {partnerRole === 'SUPPLIER' ? <p>{t('relations.modes.supplierHint')}</p> : null}
            </div>
          </div>
        </div>
      )}
    </FieldShell>
  )
}
