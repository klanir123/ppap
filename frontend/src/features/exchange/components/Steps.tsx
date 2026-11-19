import type { LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { STATUS_ICONS } from '@/components/status-icons'
import { useFormat } from '@/features/approvals/components/format'
import { statusStyle, statusToken } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import type { StepDetails } from '../api/types'
import { groupTimeline, type StepState, type TimelineStep } from '../model/steps'
import { IdText } from './parts'
import { useDuration } from './use-duration'

function StepIcon({ state, size = 'md' }: { state: StepState; size?: 'sm' | 'md' }) {
  const Icon = STATUS_ICONS.step[state] as LucideIcon
  return (
    <span
      aria-hidden="true"
      style={statusStyle(statusToken('step', state))}
      className={cn(
        'relative z-[1] flex shrink-0 items-center justify-center rounded-full border border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)',
        size === 'sm' ? 'size-6' : 'size-7',
        (state === 'PENDING' || state === 'NOT_REACHED') && 'border-dashed bg-background',
      )}
    >
      <Icon className={size === 'sm' ? 'size-3' : 'size-3.5'} strokeWidth={2.25} />
    </span>
  )
}

/** Compact horizontal flow (always visible on the card): icon per step + label, connected by a line. */
export function StepStrip({ steps }: { steps: TimelineStep[] }) {
  const { t } = useTranslation(['exchange', 'status'])
  return (
    <ol className="flex items-start overflow-x-auto pb-1" aria-label={t('record.showSteps')}>
      {steps.map((s, index) => (
        <li
          key={`${s.target}:${s.step}`}
          className={cn(
            'relative flex min-w-[5.5rem] flex-1 flex-col items-center gap-1.5 text-center',
            index > 0 && steps[index - 1]?.target !== s.target && 'ml-2',
          )}
        >
          {index > 0 ? (
            <span
              aria-hidden="true"
              className={cn(
                'absolute top-3 right-1/2 h-px w-full',
                s.state === 'PENDING' || s.state === 'NOT_REACHED' ? 'border-t border-dashed' : 'bg-border',
              )}
            />
          ) : null}
          <StepIcon state={s.state} size="sm" />
          <span className="w-full px-1 text-[11px] leading-tight break-words hyphens-auto text-muted-foreground">
            {t(`steps.${s.step}`)}
            <span className="sr-only">: {t(`status:step.${s.state}`)}</span>
          </span>
        </li>
      ))}
    </ol>
  )
}

const HIDDEN_DETAILS = ['target', 'targetRef']

const DETAIL_KEYS = [
  'shellId',
  'submodelId',
  'revision',
  'semanticId',
  'href',
  'endpoint',
  'expectedHash',
  'actualHash',
  'sha256',
  'kid',
  'assetId',
  'offerId',
  'negotiationId',
  'agreementId',
  'transferProcessId',
  'httpStatus',
  'cachedEdr',
]

function detailEntries(details: StepDetails | null | undefined): [string, unknown][] {
  if (!details) return []
  // target/targetRef are shown as group heading, not as technical detail
  const entries = Object.entries(details).filter(
    ([k, v]) => !HIDDEN_DETAILS.includes(k) && v !== null && v !== undefined && v !== '',
  )
  return entries.sort(([a], [b]) => {
    const ia = DETAIL_KEYS.indexOf(a)
    const ib = DETAIL_KEYS.indexOf(b)
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib)
  })
}

function DetailValue({ name, value, label }: { name: string; value: unknown; label: string }) {
  const { t } = useTranslation('exchange')
  if (typeof value === 'boolean')
    return <span className="font-mono text-[11px]">{value ? t('details.yes') : t('details.no')}</span>
  if (name === 'httpStatus' || typeof value === 'number')
    return <span className="font-mono text-[11px] tabular">{String(value)}</span>
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return <IdText value={text} label={label} />
}

/**
 * Detailed vertical step list with status, time, duration, hint and technical EDC details.
 * With `grouped` (pull records), steps are shown in one block per target (DTR, submodel, partner keys, document).
 */
export function StepList({ steps, grouped = false }: { steps: TimelineStep[]; grouped?: boolean }) {
  const { t } = useTranslation('exchange')
  if (!grouped) return <StepItems steps={steps} />
  return (
    <div className="space-y-5">
      {groupTimeline(steps).map((group, i) => (
        <section key={`${group.target}-${i}`} aria-label={t(`targets.${group.target}`)} className="space-y-3">
          <h4 className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            {t(`targets.${group.target}`)}
            {group.targetRef ? (
              <span className="min-w-0 font-normal tracking-normal normal-case">
                <IdText value={group.targetRef} label={t(`targets.${group.target}`)} />
              </span>
            ) : null}
          </h4>
          <StepItems steps={group.steps} />
        </section>
      ))}
    </div>
  )
}

function StepItems({ steps }: { steps: TimelineStep[] }) {
  const { t } = useTranslation(['exchange', 'status'])
  const fmt = useFormat()
  const duration = useDuration()
  return (
    <ol className="relative space-y-4">
      {steps.map((s, index) => {
        const entries = detailEntries(s.entry?.details)
        const last = index === steps.length - 1
        const stepLabel = t(`steps.${s.step}`)
        return (
          <li key={`${s.target}:${s.step}`} className="relative flex gap-3">
            {last ? null : (
              <span aria-hidden="true" className="absolute top-7 bottom-[-1rem] left-[13px] w-px bg-border" />
            )}
            <StepIcon state={s.state} />
            <div className="min-w-0 flex-1 space-y-1.5 pt-0.5">
              <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
                <p className="text-sm font-medium">
                  {stepLabel}
                  <span
                    className={cn(
                      'ml-2 text-xs font-normal',
                      s.state === 'FAILED' ? 'text-(--status-failed-fg)' : 'text-muted-foreground',
                    )}
                  >
                    {t(`status:step.${s.state}`)}
                    {s.attempts > 1 ? ` · ${t('record.stepAttempt', { count: s.attempts })}` : ''}
                  </span>
                </p>
                {s.entry ? (
                  <p className="text-xs text-muted-foreground tabular">
                    <time dateTime={s.entry.at}>{fmt.dateTime(s.entry.at)}</time>
                    {s.entry.durationMs != null ? ` · ${duration(s.entry.durationMs)}` : ''}
                  </p>
                ) : null}
              </div>
              <p className="text-xs text-muted-foreground">{t(`stepHints.${s.step}`)}</p>
              {entries.length > 0 ? (
                <dl
                  aria-label={t('details.label', { step: stepLabel })}
                  className="grid grid-cols-[minmax(7rem,auto)_minmax(0,1fr)] items-center gap-x-3 gap-y-1 rounded-md border bg-muted/40 px-3 py-2"
                >
                  {entries.map(([name, value]) => {
                    const label = t(`details.${name}` as 'details.assetId', { defaultValue: name })
                    return (
                      <div key={name} className="contents">
                        <dt className="font-mono text-[11px] text-muted-foreground">{label}</dt>
                        <dd className="min-w-0">
                          <DetailValue name={name} value={value} label={label} />
                        </dd>
                      </div>
                    )
                  })}
                </dl>
              ) : null}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
