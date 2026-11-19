import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

export interface StepperStep {
  id: string
  label: string
}

interface StepperProps {
  steps: StepperStep[]
  current: number
  /** Steps up to this index can be revisited (completed). */
  maxReached: number
  onSelect: (index: number) => void
}

/** Accessible progress indicator: ordered list, `aria-current="step"`, completed steps are buttons. */
export function Stepper({ steps, current, maxReached, onSelect }: StepperProps) {
  const { t } = useTranslation('approvals')
  return (
    <nav aria-label={t('wizard.progress')}>
      <p className="mb-2 text-xs text-muted-foreground sm:hidden" aria-hidden="true">
        {t('wizard.stepOf', { step: current + 1, total: steps.length })} · {steps[current]?.label}
      </p>
      <ol className="flex items-center gap-1 sm:gap-2">
        {steps.map((step, index) => {
          const done = index < current
          const active = index === current
          const reachable = index <= maxReached && !active
          const content = (
            <>
              <span
                aria-hidden="true"
                className={cn(
                  'flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold tabular transition-colors',
                  active && 'border-primary bg-primary text-primary-foreground',
                  done && 'border-participant bg-participant-soft text-participant-soft-foreground',
                  !active && !done && 'bg-background text-muted-foreground',
                )}
              >
                {done ? <Check className="size-3.5" strokeWidth={3} /> : index + 1}
              </span>
              <span
                className={cn('hidden truncate text-sm lg:inline', active ? 'font-medium' : 'text-muted-foreground')}
              >
                {step.label}
              </span>
              <span className="sr-only">
                {step.label}
                {done ? ` (${t('wizard.completed')})` : ''}
              </span>
            </>
          )
          return (
            <li
              key={step.id}
              className="flex min-w-0 flex-1 items-center gap-1 sm:gap-2"
              aria-current={active ? 'step' : undefined}
            >
              {reachable ? (
                <button
                  type="button"
                  onClick={() => onSelect(index)}
                  className="flex min-w-0 items-center gap-2 rounded-md p-0.5 hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
                >
                  {content}
                </button>
              ) : (
                <span className="flex min-w-0 items-center gap-2 p-0.5">{content}</span>
              )}
              {index < steps.length - 1 ? (
                <span
                  aria-hidden="true"
                  className={cn('h-px min-w-3 flex-1', index < current ? 'bg-participant' : 'bg-border')}
                />
              ) : null}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
