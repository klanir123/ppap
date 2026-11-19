import { ArrowDownLeft, ArrowUpRight, CircleHelp } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { StatusBadge } from '@/components/StatusBadge'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

import type { ExchangeDirection, ExchangeStatus } from '../api/types'

export function ExchangeStatusBadge({ status, size }: { status: ExchangeStatus; size?: 'sm' | 'md' }) {
  return <StatusBadge kind="exchange" status={status} size={size} />
}

/** Direction as icon tile: outbound ↗ (participant colour), inbound ↙ (neutral). */
export function DirectionIcon({ direction, className }: { direction: ExchangeDirection; className?: string }) {
  const Icon = direction === 'OUTBOUND' ? ArrowUpRight : ArrowDownLeft
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-8 shrink-0 items-center justify-center rounded-lg border',
        direction === 'OUTBOUND'
          ? 'border-participant/30 bg-participant-soft text-participant-soft-foreground'
          : 'border-border bg-muted text-foreground',
        className,
      )}
    >
      <Icon className="size-4" strokeWidth={2.25} />
    </span>
  )
}

/** Direction as text chip (tables). */
export function DirectionLabel({ direction }: { direction: ExchangeDirection }) {
  const { t } = useTranslation('exchange')
  const Icon = direction === 'OUTBOUND' ? ArrowUpRight : ArrowDownLeft
  return (
    <span className="inline-flex items-center gap-1 text-xs whitespace-nowrap">
      <Icon aria-hidden="true" className="size-3.5 text-muted-foreground" />
      {t(`direction.${direction}`)}
    </span>
  )
}

/** Monospace identifier, truncated, full value in title + copy button. */
export function IdText({ value, label, className }: { value: string; label: string; className?: string }) {
  const { t } = useTranslation('exchange')
  return (
    <span className={cn('inline-flex max-w-full min-w-0 items-center gap-1', className)}>
      <code className="min-w-0 truncate font-mono text-[11px]" title={value}>
        {value}
      </code>
      <CopyButton value={value} label={t('record.copyValue', { label })} />
    </span>
  )
}

/** Glossary term with explanation (keyboard: focusable button with tooltip). */
export function GlossaryTerm({ term }: { term: 'EDC' | 'DSP' | 'EDR' }) {
  const { t } = useTranslation('exchange')
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className="inline-flex items-center gap-1 rounded-md border border-dashed px-1.5 py-0.5 font-mono text-[11px] font-medium text-muted-foreground transition-colors hover:border-solid hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
          aria-label={`${t(`glossary.${term}.term`)}: ${t(`glossary.${term}.text`)}`}
        >
          {t(`glossary.${term}.term`)}
          <CircleHelp aria-hidden="true" className="size-3" />
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs text-left">{t(`glossary.${term}.text`)}</TooltipContent>
    </Tooltip>
  )
}
