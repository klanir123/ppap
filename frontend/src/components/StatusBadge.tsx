import { CircleHelp, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { STATUS_ICONS, type StatusKind } from '@/components/status-icons'

import type { ApprovalStatus, HealthStatus, RequirementLevel, ValidationStatus } from '@/lib/status'
import { statusStyle, statusToken } from '@/lib/status-style'
import { cn } from '@/lib/utils'

export type StatusBadgeProps = { className?: string; size?: 'sm' | 'md'; kind: StatusKind } & (
  | { kind: 'approval'; status: ApprovalStatus }
  | { kind: 'health'; status: HealthStatus }
  | { kind: 'validation'; status: ValidationStatus }
  | { kind: 'requirement'; status: RequirementLevel }
  | { kind: 'active'; status: boolean }
)

/**
 * Consistent status pill: colour token + icon + text (never colour alone, WCAG 1.4.1).
 * Colours come from the `--status-*` CSS variables in index.css.
 */
export function StatusBadge(props: StatusBadgeProps) {
  const { t } = useTranslation('status')
  const size = props.size ?? 'md'
  const key = String(props.status)
  const icons = STATUS_ICONS[props.kind] as Record<string, LucideIcon>
  const Icon = icons[key] ?? CircleHelp
  const label = t(`${props.kind}.${key}` as 'health.UP')
  const token = statusToken(props.kind, props.status)

  return (
    <span
      role="status"
      aria-label={t('badgeLabel', { label })}
      data-status={token}
      style={statusStyle(token)}
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
