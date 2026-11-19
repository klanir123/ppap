import { Link } from '@tanstack/react-router'
import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { ApprovalCaseDetail } from '@/features/approvals/api/types'

import { useCaseExchange } from '../api/hooks'
import { deriveDelivery } from '../model/status'
import { buildTimeline } from '../model/steps'
import { DeliveryBadge } from './Delivery'
import { RecordError } from './ExchangeRecordCard'
import { StepStrip } from './Steps'

/** Text below "Revision n eingereicht" – depends on whether the customer is reachable via Catena-X. */
export function SubmissionDeliveryText({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('exchange')
  return (
    <p className="text-sm text-muted-foreground">
      {c.source === 'MANUAL' ? t('submitted.descriptionManual') : t('submitted.description')}
    </p>
  )
}

/** Live delivery status of the PPAP_SUBMISSION message (success panel after submitting). */
export function SubmissionDelivery({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('exchange')
  const records = useCaseExchange(c.id, c.source !== 'MANUAL')
  if (c.source === 'MANUAL') return null
  const delivery = deriveDelivery(records.data, 'PPAP_SUBMISSION')
  return (
    <section className="space-y-3 rounded-lg border p-4" aria-labelledby="submission-delivery">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="submission-delivery" className="text-sm font-medium">
          {t('submitted.delivery')}
        </h2>
        <span aria-live="polite">
          <DeliveryBadge state={delivery?.state ?? 'pending'} messageType="PPAP_SUBMISSION" />
        </span>
      </div>
      {delivery ? (
        <>
          <StepStrip steps={buildTimeline(delivery.record)} />
          <RecordError record={delivery.record} />
        </>
      ) : (
        <p className="text-xs text-muted-foreground">{t('submitted.waiting')}</p>
      )}
      <Link
        to="/approvals/$caseId"
        params={{ caseId: c.id }}
        search={{ tab: 'exchange' }}
        className="inline-flex items-center gap-1 rounded-sm text-xs font-medium text-participant underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
      >
        {t('submitted.showExchange')}
        <ArrowRight aria-hidden="true" className="size-3.5" />
      </Link>
    </section>
  )
}
