import { Circle, CircleAlert, CircleCheck, CircleDashed } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { ElementState } from '../model/elements'

const ICONS = { complete: CircleCheck, issues: CircleAlert, open: Circle, optional: CircleDashed }
const COLORS = {
  complete: 'text-(--status-ok-solid)',
  issues: 'text-(--status-warning-solid)',
  open: 'text-muted-foreground',
  optional: 'text-muted-foreground/70',
}

export function ElementStateIcon({ state, className }: { state: ElementState; className?: string }) {
  const { t } = useTranslation('approvals')
  const Icon = ICONS[state]
  return (
    <span className={cn('inline-flex', className)}>
      <Icon aria-hidden="true" className={cn('size-4', COLORS[state])} strokeWidth={2.25} />
      <span className="sr-only">{t(`elementState.${state}`)}</span>
    </span>
  )
}

export function EvidenceTypeLabel({ type }: { type: string }) {
  const { t } = useTranslation('approvals')
  return (
    <span className="text-xs whitespace-nowrap text-muted-foreground">
      {t(`evidence.${type}` as 'evidence.DOCUMENT', { defaultValue: type })}
    </span>
  )
}
