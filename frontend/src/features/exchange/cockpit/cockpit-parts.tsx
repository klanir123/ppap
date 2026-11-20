import { Bot, CircleCheck, CircleDashed, CircleX, Hand, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Card, CardContent } from '@/components/ui/card'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { extractConstraints, operatorSymbol, type OdrlConstraint } from '../model/odrl'
import { processTone, type ProcessTone } from './process-states'

const chip =
  'inline-flex h-5 shrink-0 items-center gap-1 rounded-md border px-1.5 text-[11px] font-medium whitespace-nowrap'

/** "Vom Connector verwaltet" (created by the provisioning) vs. created manually / by others. */
export function ManagedBadge({ managed }: { managed: boolean }) {
  const { t } = useTranslation('exchange')
  const Icon = managed ? Bot : Hand
  return (
    <span
      className={cn(
        chip,
        managed
          ? 'border-participant/30 bg-participant-soft text-participant-soft-foreground'
          : 'border-dashed bg-background text-muted-foreground',
      )}
      title={managed ? t('cockpit.managed.hintManaged') : t('cockpit.managed.hintOther')}
    >
      <Icon aria-hidden="true" className="size-3" />
      {managed ? t('cockpit.managed.managed') : t('cockpit.managed.other')}
    </span>
  )
}

/** Constraint chips of an ODRL policy (e.g. FrameworkAgreement = DataExchangeGovernance:1.0). */
export function ConstraintChips({
  policy,
  constraints,
  empty,
}: {
  policy?: unknown
  constraints?: OdrlConstraint[]
  empty?: ReactNode
}) {
  const { t } = useTranslation('exchange')
  const list = constraints ?? extractConstraints(policy)
  if (list.length === 0)
    return <>{empty ?? <span className="text-xs text-muted-foreground">{t('cockpit.policy.noConstraints')}</span>}</>
  return (
    <ul className="flex flex-wrap gap-1" aria-label={t('cockpit.policy.constraints')}>
      {list.map((c, i) => (
        <li
          key={`${c.leftOperand}-${i}`}
          className={cn(
            chip,
            'h-auto min-h-5 bg-muted/50 font-mono font-normal whitespace-normal',
            c.rule === 'prohibition' && 'border-(--status-error-border)',
          )}
          title={`${c.rule}${c.action ? ` · ${c.action}` : ''}`}
        >
          <span className="font-semibold">{c.leftOperand}</span>
          <span aria-hidden="true" className="text-muted-foreground">
            {operatorSymbol(c.operator)}
          </span>
          <span className="sr-only">{c.operator}</span>
          <span className="break-all">{c.rightOperand}</span>
        </li>
      ))}
    </ul>
  )
}

const TONE: Record<ProcessTone, { token: string; icon: LucideIcon }> = {
  ok: { token: 'ok', icon: CircleCheck },
  error: { token: 'error', icon: CircleX },
  progress: { token: 'in-progress', icon: CircleDashed },
}

/** EDC state machine value (negotiation / transfer) – raw EDC term with colour + icon. */
export function ProcessStateBadge({ state }: { state: string }) {
  const { t } = useTranslation('exchange')
  const tone = TONE[processTone(state)]
  const Icon = tone.icon
  return (
    <span
      role="status"
      aria-label={t('cockpit.processes.stateLabel', { state })}
      style={statusStyle(tone.token)}
      className="inline-flex h-5 items-center gap-1 rounded-full border border-(--sb-border) bg-(--sb-bg) px-2 font-mono text-[10.5px] font-medium text-(--sb-fg)"
    >
      <Icon aria-hidden="true" className="size-3" />
      {state}
    </span>
  )
}

export function StatCard({
  icon: Icon,
  title,
  children,
  aside,
  className,
}: {
  icon: LucideIcon
  title: ReactNode
  children: ReactNode
  aside?: ReactNode
  className?: string
}) {
  return (
    <Card className={cn('gap-0 py-4', className)}>
      <CardContent className="space-y-2.5 px-4">
        <div className="flex items-center justify-between gap-2">
          <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
            <Icon aria-hidden="true" className="size-4" />
            {title}
          </p>
          {aside}
        </div>
        {children}
      </CardContent>
    </Card>
  )
}
