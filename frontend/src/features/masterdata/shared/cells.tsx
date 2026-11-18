import { CircleCheck, CircleSlash } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

import type { PartnerRole } from '../api/types'

export function MonoText({ children, className }: { children: React.ReactNode; className?: string }) {
  return <span className={cn('font-mono text-xs whitespace-nowrap', className)}>{children}</span>
}

export function Muted({ children }: { children: React.ReactNode }) {
  return <span className="text-muted-foreground">{children}</span>
}

export function ActiveBadge({ active }: { active: boolean }) {
  return <StatusBadge kind="active" status={active} size="sm" />
}

export function RoleChips({ roles }: { roles: PartnerRole[] }) {
  const { t } = useTranslation('masterdata')
  return (
    <span className="flex flex-wrap gap-1">
      {roles.map((role) => (
        <span
          key={role}
          className="rounded-md border bg-muted/60 px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap"
        >
          {t(`partners.roleValues.${role}`)}
        </span>
      ))}
    </span>
  )
}

/** AK-01-5: partner without DSP endpoint/DID can be saved, but not used for sending. */
export function ExchangeReadyIndicator({ ready }: { ready: boolean }) {
  const { t } = useTranslation('masterdata')
  const label = ready ? t('partners.exchangeReady') : t('partners.exchangeNotReady')
  const hint = ready ? t('partners.exchangeReadyHint') : t('partners.exchangeNotReadyHint')
  const Icon = ready ? CircleCheck : CircleSlash
  const token = ready ? 'ok' : 'warning'
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          aria-label={`${label}: ${hint}`}
          className="inline-flex h-5 items-center gap-1.5 rounded-full border border-(--c-border) bg-(--c-bg) px-2 text-[11px] font-medium whitespace-nowrap text-(--c-fg) focus-visible:outline-2 focus-visible:outline-ring"
          style={
            {
              '--c-bg': `var(--status-${token}-bg)`,
              '--c-fg': `var(--status-${token}-fg)`,
              '--c-border': `var(--status-${token}-border)`,
            } as React.CSSProperties
          }
        >
          <Icon aria-hidden="true" className="size-3" strokeWidth={2.25} />
          {label}
        </button>
      </TooltipTrigger>
      <TooltipContent className="max-w-xs">{hint}</TooltipContent>
    </Tooltip>
  )
}
