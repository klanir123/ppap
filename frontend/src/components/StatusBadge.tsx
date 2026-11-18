import {
  Ban,
  CircleCheck,
  CircleDashed,
  CircleDot,
  CircleHelp,
  CircleX,
  Clock,
  Eye,
  FileEdit,
  Send,
  ShieldAlert,
  type LucideIcon,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'
import type { ApprovalStatus, HealthStatus } from '@/lib/status'

type StatusToken = ApprovalStatus | 'up' | 'down' | 'unknown'

const APPROVAL_ICONS: Record<ApprovalStatus, LucideIcon> = {
  draft: FileEdit,
  requested: Clock,
  'in-progress': CircleDashed,
  submitted: Send,
  'in-review': Eye,
  approved: CircleCheck,
  'conditionally-approved': ShieldAlert,
  rejected: CircleX,
  cancelled: Ban,
}

const HEALTH_ICONS: Record<HealthStatus, LucideIcon> = {
  UP: CircleCheck,
  DOWN: CircleX,
  UNKNOWN: CircleHelp,
}

export type StatusBadgeProps =
  | { kind: 'approval'; status: ApprovalStatus; className?: string; size?: 'sm' | 'md' }
  | { kind: 'health'; status: HealthStatus; className?: string; size?: 'sm' | 'md' }

/**
 * Consistent status pill: colour token + icon + text (never colour alone, WCAG 1.4.1).
 * Colours come from the `--status-*` CSS variables in index.css.
 */
export function StatusBadge(props: StatusBadgeProps) {
  const { t } = useTranslation('status')
  const size = props.size ?? 'md'

  let token: StatusToken
  let label: string
  let Icon: LucideIcon
  if (props.kind === 'approval') {
    token = props.status
    label = t(`approval.${props.status}`)
    Icon = APPROVAL_ICONS[props.status] ?? CircleDot
  } else {
    token = props.status.toLowerCase() as StatusToken
    label = t(`health.${props.status}`)
    Icon = HEALTH_ICONS[props.status] ?? CircleHelp
  }

  return (
    <span
      role="status"
      aria-label={t('badgeLabel', { label })}
      data-status={token}
      style={
        {
          '--sb-bg': `var(--status-${token}-bg)`,
          '--sb-fg': `var(--status-${token}-fg)`,
          '--sb-border': `var(--status-${token}-border)`,
        } as React.CSSProperties
      }
      className={cn(
        'inline-flex shrink-0 items-center gap-1.5 rounded-full border font-medium whitespace-nowrap',
        'border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)',
        size === 'sm' ? 'h-5 px-2 text-[11px]' : 'h-6 px-2.5 text-xs',
        props.className,
      )}
    >
      <Icon aria-hidden="true" className={size === 'sm' ? 'size-3' : 'size-3.5'} strokeWidth={2.25} />
      <span>{label}</span>
    </span>
  )
}
