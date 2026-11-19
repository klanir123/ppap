import { useTranslation } from 'react-i18next'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

import { useLiveStatus } from './live-context'
import type { LiveStatus } from './sse'

type Display = 'open' | 'connecting' | 'reconnecting' | 'paused' | 'offline'

function display(status: LiveStatus): Display {
  if (status === 'idle' || status === 'closed') return 'offline'
  return status
}

const DOT: Record<Display, string> = {
  open: 'bg-(--status-ok-solid)',
  connecting: 'bg-(--status-warning-solid) animate-pulse motion-reduce:animate-none',
  reconnecting: 'bg-(--status-warning-solid) animate-pulse motion-reduce:animate-none',
  paused: 'bg-(--status-draft-solid)',
  offline: 'bg-(--status-draft-solid)',
}

/** Subtle connection indicator for live updates (top bar). */
export function LiveIndicator() {
  const { t } = useTranslation('exchange')
  const state = display(useLiveStatus())
  const label = t(`live.${state}`)
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={t('live.label', { state: label })}
          data-live={state}
          className="inline-flex h-7 cursor-default items-center gap-1.5 rounded-full border px-2 text-[11px] font-medium text-muted-foreground focus-visible:outline-2 focus-visible:outline-ring"
        >
          <span className="relative flex size-2">
            {state === 'open' ? (
              <span
                aria-hidden="true"
                className="absolute inline-flex size-full animate-ping rounded-full bg-(--status-ok-solid) opacity-40 [animation-duration:2.5s] [animation-iteration-count:2] motion-reduce:hidden"
              />
            ) : null}
            <span aria-hidden="true" className={cn('relative inline-flex size-2 rounded-full', DOT[state])} />
          </span>
          <span className="hidden sm:inline">{label}</span>
        </button>
      </TooltipTrigger>
      <TooltipContent>{t(`live.${state}Hint`)}</TooltipContent>
    </Tooltip>
  )
}
