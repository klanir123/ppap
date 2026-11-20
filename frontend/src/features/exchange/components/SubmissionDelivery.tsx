import { Link } from '@tanstack/react-router'
import { ArrowRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { ApprovalCaseDetail } from '@/features/approvals/api/types'
import { latestSubmission, revisionExchangeMode } from '@/features/approvals/model/submissions'

import { useCaseExchange } from '../api/hooks'
import type { ExchangeRecord } from '../api/types'
import { deriveDelivery } from '../model/status'
import { buildTimeline } from '../model/steps'
import { DeliveryBadge } from './Delivery'
import { RecordError } from './ExchangeRecordCard'
import { StepStrip } from './Steps'

/** Exchange mode the latest revision was submitted with (PULL → no notification, the customer polls). */
function useSubmittedMode(c: ApprovalCaseDetail) {
  return revisionExchangeMode(latestSubmission(c.submissions.filter((s) => s.status === 'SUBMITTED')))
}

/** Text below "Revision n eingereicht" – depends on whether the customer is reachable via Catena-X and the mode. */
export function SubmissionDeliveryText({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('exchange')
  const mode = useSubmittedMode(c)
  return (
    <p className="text-sm text-muted-foreground">
      {c.source === 'MANUAL'
        ? t('submitted.descriptionManual')
        : mode === 'PULL'
          ? t('submitted.descriptionPull')
          : t('submitted.description')}
    </p>
  )
}

/**
 * Live status of how the submission reaches the customer (success panel after submitting):
 * PUSH → PPAP_SUBMISSION, PUSH_NOTIFY_PULL_PAYLOAD → PPAP_SUBMISSION_AVAILABLE, PULL → twin registration only.
 */
export function SubmissionDelivery({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('exchange')
  const mode = useSubmittedMode(c)
  const pull = mode === 'PULL'
  const deliveryOf = (records: ExchangeRecord[] | undefined) =>
    pull
      ? deriveDelivery(records, 'TWIN_REGISTRATION')
      : deriveDelivery(records, ['PPAP_SUBMISSION', 'PPAP_SUBMISSION_AVAILABLE'])
  // keep refreshing until the delivery is settled (live events may overtake the stored status)
  const records = useCaseExchange(
    c.id,
    c.source !== 'MANUAL',
    (list) => deliveryOf(list)?.state !== 'delivered' && deliveryOf(list)?.state !== 'failed',
  )
  if (c.source === 'MANUAL') return null
  const delivery = deliveryOf(records.data)
  const messageType = delivery?.record.messageType ?? (pull ? 'TWIN_REGISTRATION' : 'PPAP_SUBMISSION')
  return (
    <section className="space-y-3 rounded-lg border p-4" aria-labelledby="submission-delivery">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="submission-delivery" className="text-sm font-medium">
          {pull ? t('submitted.deliveryPull') : t('submitted.delivery')}
        </h2>
        <span aria-live="polite">
          <DeliveryBadge
            state={delivery?.state ?? 'pending'}
            messageType={messageType}
            label={pull && delivery?.state === 'delivered' ? t('delivery.provided') : undefined}
          />
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
