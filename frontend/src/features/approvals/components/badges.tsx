import { AlarmClock, Building2, CalendarClock, Cable, CircleSlash, Factory, History, PenLine } from 'lucide-react'
import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { StatusBadge } from '@/components/StatusBadge'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import type { CaseRole, CaseSource, CaseStatus, SignatureStatus } from '../api/types'
import { caseStatusToken, daysUntil, isOpen } from '../model/status'
import { shortHash, useFormat } from './format'

export function CaseStatusBadge({ status, size }: { status: CaseStatus; size?: 'sm' | 'md' }) {
  return <StatusBadge kind="approval" status={caseStatusToken(status)} size={size} />
}

export function SignatureBadge({ status, size }: { status: SignatureStatus; size?: 'sm' | 'md' }) {
  return <StatusBadge kind="signature" status={status} size={size} />
}

const chip =
  'inline-flex h-5 shrink-0 items-center gap-1 rounded-md border px-1.5 text-[11px] font-medium whitespace-nowrap'

/** Own role in the case (customer = we requested, supplier = we deliver). */
export function RoleBadge({ role, className }: { role: CaseRole; className?: string }) {
  const { t } = useTranslation('approvals')
  const Icon = role === 'CUSTOMER' ? Building2 : Factory
  return (
    <span
      className={cn(
        chip,
        role === 'CUSTOMER'
          ? 'border-participant/30 bg-participant-soft text-participant-soft-foreground'
          : 'border-border bg-muted/70 text-foreground',
        className,
      )}
      title={t(`role.hint.${role}`)}
    >
      <Icon aria-hidden="true" className="size-3" />
      <span className="sr-only">{t('role.label')}: </span>
      {t(`role.${role}`)}
    </span>
  )
}

const SOURCE_ICON = { MANUAL: PenLine, LEGACY_IMPORT: History, EXCHANGE: Cable, LOCAL: Cable } as const

/** Origin of the case; LOCAL is the default and only shown when `showLocal`. */
export function SourceBadge({ source, showLocal }: { source: CaseSource; showLocal?: boolean }) {
  const { t } = useTranslation('approvals')
  if (source === 'LOCAL' && !showLocal) return null
  const Icon = SOURCE_ICON[source]
  return (
    <span className={cn(chip, 'border-dashed bg-background text-muted-foreground')} title={t(`source.hint.${source}`)}>
      <Icon aria-hidden="true" className="size-3" />
      {t(`source.${source}`)}
    </span>
  )
}

/** Due date with relative hint; overdue = colour + icon + text. */
export function DueDate({
  dueDate,
  overdue,
  status,
  compact,
}: {
  dueDate?: string | null
  overdue?: boolean
  status?: CaseStatus
  compact?: boolean
}) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  if (!dueDate) return <span className="text-muted-foreground">–</span>
  const open = status ? isOpen(status) : true
  const days = daysUntil(dueDate)
  const isOverdue = open && (overdue ?? days < 0)
  const relative = !open
    ? undefined
    : isOverdue
      ? t('due.overdue', { count: Math.max(1, -days) })
      : days === 0
        ? t('due.today')
        : t('due.inDays', { count: days })
  const style: CSSProperties | undefined = isOverdue ? statusStyle('error') : undefined
  return (
    <span
      style={style}
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap tabular',
        isOverdue && 'rounded-md bg-(--sb-bg) px-1.5 py-0.5 font-medium text-(--sb-fg)',
      )}
    >
      {isOverdue ? (
        <AlarmClock aria-hidden="true" className="size-3.5" />
      ) : compact ? null : (
        <CalendarClock aria-hidden="true" className="size-3.5 text-muted-foreground" />
      )}
      <time dateTime={dueDate}>{fmt.date(dueDate)}</time>
      {relative && !compact ? <span className={cn(!isOverdue && 'text-muted-foreground')}>· {relative}</span> : null}
      {relative && compact && isOverdue ? <span className="sr-only">({relative})</span> : null}
    </span>
  )
}

/** Short SHA-256 with copy button; full value in the title and for screen readers. */
export function HashText({ hash, label }: { hash: string; label?: string }) {
  const { t } = useTranslation('approvals')
  return (
    <span className="inline-flex items-center gap-1">
      <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]" title={hash}>
        <span aria-hidden="true">{shortHash(hash)}…</span>
        <span className="sr-only">{hash}</span>
      </code>
      <CopyButton value={hash} label={label ?? t('hash.copy')} />
    </span>
  )
}

export function PartnerText({ name, bpnl }: { name?: string; bpnl: string }) {
  return (
    <span className="flex min-w-0 flex-col">
      <span className="truncate">{name ?? bpnl}</span>
      {name ? <span className="font-mono text-[11px] text-muted-foreground">{bpnl}</span> : null}
    </span>
  )
}

/** Non-interactive Catena-X readiness badge (for use inside selectable cards). */
export function ExchangeReadyBadge({ ready }: { ready: boolean }) {
  const { t } = useTranslation('approvals')
  return (
    <span
      style={statusStyle(ready ? 'ok' : 'warning')}
      className={cn(chip, 'rounded-full border-(--sb-border) bg-(--sb-bg) px-2 text-(--sb-fg)')}
    >
      {ready ? <Cable aria-hidden="true" className="size-3" /> : <CircleSlash aria-hidden="true" className="size-3" />}
      {ready ? t('partnerReady.ready') : t('partnerReady.notReady')}
    </span>
  )
}
