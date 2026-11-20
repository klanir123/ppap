import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import { FLOW_CATEGORIES, flowColor } from './model/flows'
import { useFlowLabels } from './use-flow-labels'

/** Colour legend of the message categories (identity is never colour alone: labels travel with the dots). */
export function DataspaceLegend({ className }: { className?: string }) {
  const { t } = useTranslation('dataspace')
  const labels = useFlowLabels()
  return (
    <div className={cn('flex flex-wrap items-center gap-x-4 gap-y-1.5', className)}>
      <span className="text-muted-foreground">{t('legend.title')}</span>
      <ul
        className="flex flex-wrap items-center gap-x-4 gap-y-1.5"
        aria-label={t('legend.title')}
        data-testid="dataspace-legend"
      >
        {FLOW_CATEGORIES.map((c) => (
          <li key={c} className="inline-flex items-center gap-1.5">
            <span
              aria-hidden="true"
              className="size-2.5 rounded-full ring-2 ring-background"
              style={{ background: flowColor(c) }}
            />
            {labels.category(c)}
          </li>
        ))}
      </ul>
      <span className="text-muted-foreground">{t('legend.direction')}</span>
    </div>
  )
}
