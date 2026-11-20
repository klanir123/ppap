import { ArrowDownLeft, ArrowUpRight, Inbox } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useRelativeTime } from '@/features/approvals/components/format'
import { cn } from '@/lib/utils'

import type { DataspaceMessage } from './api'
import { MessageSteps } from './MessageSteps'
import { flowCategory, flowColor, flowDirection } from './model/flows'
import { useFlowLabels } from './use-flow-labels'

/** Latest messages of the own connector; hover / focus highlights the edge and shows case + steps. */
export function RecentMessages({
  messages,
  onHighlight,
  limit = 8,
  className,
}: {
  messages: DataspaceMessage[]
  onHighlight: (partner: string | undefined) => void
  limit?: number
  className?: string
}) {
  const { t } = useTranslation('dataspace')
  const labels = useFlowLabels()
  const relative = useRelativeTime()
  const list = messages.filter((m) => flowCategory(m.messageType)).slice(0, limit)
  return (
    <section className={cn('flex min-h-0 flex-col gap-3', className)} aria-labelledby="dataspace-recent">
      <h2 id="dataspace-recent" className="text-sm font-semibold">
        {t('recent.title')}
      </h2>
      {list.length === 0 ? (
        <p className="flex flex-col items-center gap-2 rounded-lg border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
          <Inbox aria-hidden="true" className="size-5" />
          {t('recent.empty')}
        </p>
      ) : (
        <ol className="min-h-0 space-y-1.5 overflow-y-auto" data-testid="dataspace-recent">
          {list.map((m) => {
            const category = flowCategory(m.messageType)!
            const toPartner = flowDirection(m) === 'toPartner'
            const Arrow = toPartner ? ArrowUpRight : ArrowDownLeft
            const partner = m.partner.name ?? m.partner.bpnl
            return (
              <li key={m.id}>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <button
                      type="button"
                      onMouseEnter={() => onHighlight(m.partner.bpnl)}
                      onMouseLeave={() => onHighlight(undefined)}
                      onFocus={() => onHighlight(m.partner.bpnl)}
                      onBlur={() => onHighlight(undefined)}
                      className="flex w-full items-start gap-2.5 rounded-lg border bg-card px-3 py-2 text-left transition-colors hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring"
                    >
                      <span
                        aria-hidden="true"
                        className="mt-1.5 size-2.5 shrink-0 rounded-full"
                        style={{ background: flowColor(category) }}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1 text-[0.8125em] font-medium">
                          {labels.type(m.messageType)}
                          <Arrow aria-hidden="true" className="size-3.5 text-muted-foreground" />
                        </span>
                        <span className="block truncate text-[0.75em] text-muted-foreground">
                          {toPartner ? t('recent.to', { partner }) : t('recent.from', { partner })}
                          {m.caseNumber ? ` · ${m.caseNumber}` : ''}
                        </span>
                      </span>
                      <time dateTime={m.at} className="shrink-0 text-[0.6875em] text-muted-foreground tabular">
                        {relative(m.at)}
                      </time>
                    </button>
                  </TooltipTrigger>
                  <TooltipContent side="left" className="max-w-xs space-y-1 text-left">
                    <p className="font-semibold">
                      {labels.type(m.messageType)}
                      {m.caseNumber ? ` · ${m.caseNumber}` : ''}
                    </p>
                    <MessageSteps message={m} inverted />
                  </TooltipContent>
                </Tooltip>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
