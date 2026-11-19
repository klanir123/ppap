import { TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { ApprovalCase } from '@/features/approvals/api/types'
import { statusStyle } from '@/lib/status-style'

import { caseClarification } from '../api/client'

/** Marker for cases in the clarification list (tables, cards). */
export function ClarificationChip({ approvalCase }: { approvalCase: ApprovalCase }) {
  const { t } = useTranslation('exchange')
  const clarification = caseClarification(approvalCase)
  if (!clarification) return null
  const number = clarification.partnerPartNumber ?? approvalCase.partnerPartNumber
  return (
    <span
      style={statusStyle('warning')}
      title={number ? t('clarification.title', { number }) : t('clarification.titleUnknown')}
      className="inline-flex h-5 shrink-0 items-center gap-1 rounded-md border border-(--sb-border) bg-(--sb-bg) px-1.5 text-[11px] font-medium whitespace-nowrap text-(--sb-fg)"
    >
      <TriangleAlert aria-hidden="true" className="size-3" />
      {t('clarification.badge')}
    </span>
  )
}
