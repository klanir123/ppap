import { Link } from '@tanstack/react-router'
import { CircleCheck, CircleX, Loader2, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { useCaseExchange } from '../api/hooks'
import type { LastOutbound, MessageType } from '../api/types'
import { deliveryFromLastOutbound, deriveDelivery, type DeliveryState } from '../model/status'
import { useMessageTypeLabel } from './use-labels'

const DELIVERY_META: Record<DeliveryState, { icon: LucideIcon; token: string }> = {
  delivered: { icon: CircleCheck, token: 'delivered' },
  pending: { icon: Loader2, token: 'in-progress' },
  failed: { icon: CircleX, token: 'failed' },
}

/** Small delivery pill: Zugestellt / Wird zugestellt … / Zustellung fehlgeschlagen. */
export function DeliveryBadge({
  state,
  messageType,
  className,
  label: labelOverride,
}: {
  state: DeliveryState
  messageType?: string
  className?: string
  /** Replaces the state text (e.g. "Bereitgestellt" for a pull-mode twin registration). */
  label?: string
}) {
  const { t } = useTranslation('exchange')
  const typeLabel = useMessageTypeLabel()
  const { icon: Icon, token } = DELIVERY_META[state]
  const label = labelOverride ?? t(`delivery.${state}`)
  return (
    <span
      data-delivery={state}
      style={statusStyle(token)}
      className={cn(
        'inline-flex h-5 shrink-0 items-center gap-1 rounded-full border border-(--sb-border) bg-(--sb-bg) px-2 text-[11px] font-medium whitespace-nowrap text-(--sb-fg)',
        className,
      )}
    >
      <Icon
        aria-hidden="true"
        className={cn('size-3', state === 'pending' && 'animate-spin motion-reduce:animate-none')}
        strokeWidth={2.25}
      />
      {messageType ? (
        <span className="sr-only">{t('delivery.label', { type: typeLabel(messageType), state: label })}</span>
      ) : null}
      <span aria-hidden={messageType ? true : undefined}>{label}</span>
    </span>
  )
}

/**
 * Delivery state of the case's latest outbound message, as a link to the exchange tab.
 * Renders nothing while loading or when the case has no outbound message.
 */
export function CaseDeliveryIndicator({ caseId, messageType }: { caseId: string; messageType?: MessageType }) {
  const { t } = useTranslation('exchange')
  const typeLabel = useMessageTypeLabel()
  const records = useCaseExchange(caseId)
  const delivery = deriveDelivery(records.data, messageType)
  if (!delivery) return null
  const type = delivery.record.messageType
  return (
    <Link
      to="/approvals/$caseId"
      params={{ caseId }}
      search={{ tab: 'exchange' }}
      className="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      title={`${typeLabel(type)} · ${t('delivery.open')}`}
      aria-label={`${t('delivery.label', { type: typeLabel(type), state: t(`delivery.${delivery.state}`) })} – ${t('delivery.open')}`}
    >
      <DeliveryBadge state={delivery.state} />
    </Link>
  )
}

/** List rows: delivery state of the latest outbound message from the case summary (`lastOutbound`). */
export function LastOutboundBadge({ last }: { last: LastOutbound | null | undefined }) {
  const state = deliveryFromLastOutbound(last)
  if (!state || !last) return null
  return <DeliveryBadge state={state} messageType={last.messageType} />
}
