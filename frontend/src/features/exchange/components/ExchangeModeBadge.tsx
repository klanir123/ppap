import { ArrowDownToLine, ArrowUpFromLine, BellRing, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { ExchangeMode } from '@/features/approvals/api/types'
import { cn } from '@/lib/utils'

const ICONS: Record<ExchangeMode, LucideIcon> = {
  PUSH: ArrowUpFromLine,
  PULL: ArrowDownToLine,
  PUSH_NOTIFY_PULL_PAYLOAD: BellRing,
}

/**
 * Effective exchange mode of a revision (Push / Pull / Benachrichtigung + Pull) as a small neutral chip;
 * focusable, the tooltip explains how the package reached the customer.
 */
export function ExchangeModeBadge({
  mode,
  className,
  interactive = true,
}: {
  mode: ExchangeMode
  className?: string
  /** false inside other interactive elements (e.g. a list button): plain chip without tooltip/focus. */
  interactive?: boolean
}) {
  const { t } = useTranslation('exchange')
  const Icon = ICONS[mode]
  const label = t(`mode.${mode}.short`)
  const hint = t(`mode.${mode}.hint`)
  const chipClass = cn(
    'inline-flex h-5 shrink-0 items-center gap-1 rounded-md border bg-muted/60 px-1.5 text-[11px] font-medium whitespace-nowrap text-muted-foreground',
    className,
  )
  if (!interactive) {
    return (
      <span data-exchange-mode={mode} className={chipClass}>
        <Icon aria-hidden="true" className="size-3" strokeWidth={2.25} />
        <span className="sr-only">{t('mode.label')}: </span>
        {label}
      </span>
    )
  }
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          data-exchange-mode={mode}
          aria-label={`${t('mode.badgeLabel', { mode: label })}. ${hint}`}
          className={cn(chipClass, 'cursor-default focus-visible:outline-2 focus-visible:outline-ring')}
        >
          <Icon aria-hidden="true" className="size-3" strokeWidth={2.25} />
          {label}
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs text-left">
        <div className="space-y-0.5">
          <p className="font-medium">{t('mode.badgeLabel', { mode: label })}</p>
          <p>{hint}</p>
        </div>
      </TooltipContent>
    </Tooltip>
  )
}
