import { CircleCheck, CircleDashed, CircleX, type LucideIcon } from 'lucide-react'
import { Fragment } from 'react'
import { useTranslation } from 'react-i18next'

import { OUTBOUND_FLOW, INBOUND_FLOW } from '@/features/exchange/model/steps'
import { cn } from '@/lib/utils'

import type { DataspaceMessage } from './api'

const ICON: Record<string, LucideIcon> = { OK: CircleCheck, FAILED: CircleX }

/**
 * Compact step chain of a message (Katalog → Vertrag → EDR → Zustellung …): recorded steps with their
 * status; without recorded steps the expected flow is shown as pending.
 */
export function MessageSteps({
  message,
  inverted,
}: {
  message: Pick<DataspaceMessage, 'steps' | 'direction'>
  inverted?: boolean
}) {
  const { t } = useTranslation(['exchange', 'dataspace', 'status'])
  const recorded = message.steps ?? []
  const steps = recorded.length
    ? dedupe(recorded.map((s) => ({ step: String(s.step), status: s.status })))
    : (message.direction === 'OUTBOUND' ? OUTBOUND_FLOW : INBOUND_FLOW).map((step) => ({
        step: String(step),
        status: 'PENDING',
      }))
  return (
    <ol className="flex flex-wrap items-center gap-x-1 gap-y-0.5 text-[11px]" aria-label={t('dataspace:stepsLabel')}>
      {steps.map((s, i) => {
        const Icon = ICON[s.status] ?? CircleDashed
        return (
          <Fragment key={`${s.step}-${i}`}>
            {i > 0 ? (
              <li aria-hidden="true" className={cn(inverted ? 'opacity-60' : 'text-muted-foreground')}>
                →
              </li>
            ) : null}
            <li className="inline-flex items-center gap-0.5 whitespace-nowrap" data-status={s.status}>
              <Icon
                aria-hidden="true"
                className={cn(
                  'size-3',
                  !inverted && s.status === 'OK' && 'text-(--status-ok-solid)',
                  !inverted && s.status === 'FAILED' && 'text-(--status-error-solid)',
                  !inverted && s.status !== 'OK' && s.status !== 'FAILED' && 'text-muted-foreground',
                )}
              />
              {t(`exchange:steps.${s.step}` as 'exchange:steps.CATALOG', { defaultValue: s.step })}
              <span className="sr-only">
                ({t(`status:step.${s.status}` as 'status:step.OK', { defaultValue: s.status })})
              </span>
            </li>
          </Fragment>
        )
      })}
    </ol>
  )
}

/** Last status per step name (retries record a step several times). */
function dedupe(steps: { step: string; status: string }[]) {
  const map = new Map<string, { step: string; status: string }>()
  for (const s of steps) map.set(s.step, s)
  return [...map.values()]
}
