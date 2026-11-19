import { ChevronDown, Loader2, RotateCw, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { usePermission } from '@/features/auth/current-user'
import { HashText } from '@/features/approvals/components/badges'
import { useFormat } from '@/features/approvals/components/format'
import { cn } from '@/lib/utils'

import { useRetryMessage } from '../api/hooks'
import type { ExchangeRecord } from '../api/types'
import { canRetry, isFailure, isPullRecord } from '../model/status'
import { buildTimeline, totalDuration } from '../model/steps'
import { DirectionIcon, ExchangeStatusBadge, IdText } from './parts'
import { useDuration } from './use-duration'
import { StepList, StepStrip } from './Steps'
import { useMessageTypeLabel } from './use-labels'

/** Retry (outbound FAILED → resend, inbound REJECTED → reprocess); CONNECTOR_ADMIN only. */
export function RetryButton({ record, size = 'sm' }: { record: ExchangeRecord; size?: 'sm' | 'xs' }) {
  const { t } = useTranslation('exchange')
  const allowed = usePermission('exchange:retry')
  const retry = useRetryMessage()
  if (!allowed || !canRetry(record)) return null
  const outbound = record.direction === 'OUTBOUND'
  const pull = isPullRecord(record.messageType)
  return (
    <Button
      variant="outline"
      size={size}
      disabled={retry.isPending}
      onClick={() =>
        retry.mutate(record.id, {
          onSuccess: () =>
            toast.success(
              pull ? t('record.refetchSuccess') : outbound ? t('record.retrySuccess') : t('record.reprocessSuccess'),
            ),
        })
      }
    >
      {retry.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <RotateCw aria-hidden="true" />}
      {pull ? t('record.refetch') : outbound ? t('record.retry') : t('record.reprocess')}
    </Button>
  )
}

/** Last error of a failed/rejected message – prominent, with retry. */
export function RecordError({ record }: { record: ExchangeRecord }) {
  const { t } = useTranslation('exchange')
  if (!isFailure(record.status)) return null
  return (
    <Alert variant="destructive" className="border-(--status-failed-border) bg-(--status-failed-bg)/60">
      <TriangleAlert aria-hidden="true" />
      <AlertTitle>
        {isPullRecord(record.messageType)
          ? t('record.lastErrorPull')
          : record.direction === 'OUTBOUND'
            ? t('record.lastError')
            : t('record.lastErrorInbound')}
      </AlertTitle>
      <AlertDescription className="space-y-2">
        {record.errorCode ? (
          <p>
            <code className="rounded bg-background/70 px-1.5 py-0.5 font-mono text-[11px]">{record.errorCode}</code>
          </p>
        ) : null}
        {record.lastError ? (
          <p className="font-mono text-xs break-words whitespace-pre-line">{record.lastError}</p>
        ) : null}
        <RetryButton record={record} />
      </AlertDescription>
    </Alert>
  )
}

/** Meta line: created, attempts, next attempt, total duration. */
export function RecordMeta({ record }: { record: ExchangeRecord }) {
  const { t } = useTranslation('exchange')
  const fmt = useFormat()
  const duration = useDuration()
  const total = totalDuration(record.steps)
  const parts = [
    <time key="at" dateTime={record.createdAt} className="tabular">
      {fmt.dateTime(record.createdAt)}
    </time>,
    <span key="attempts">{t('record.attempts', { count: record.attempts })}</span>,
  ]
  if (record.nextAttemptAt && record.status !== 'DELIVERED') {
    parts.push(<span key="next">{t('record.nextAttempt', { at: fmt.dateTime(record.nextAttemptAt) })}</span>)
  }
  if (total !== undefined) parts.push(<span key="dur">{t('record.totalDuration', { duration: duration(total) })}</span>)
  return (
    <p className="flex flex-wrap items-center gap-x-1.5 text-xs text-muted-foreground">
      {parts.map((p, i) => (
        <span key={i} className="inline-flex items-center gap-1.5">
          {i > 0 ? <span aria-hidden="true">·</span> : null}
          {p}
        </span>
      ))}
    </p>
  )
}

/** Identifiers (payload hash, correlation id, message id). */
export function RecordIdentifiers({ record, className }: { record: ExchangeRecord; className?: string }) {
  const { t } = useTranslation('exchange')
  return (
    <dl className={cn('grid gap-x-6 gap-y-2 text-xs sm:grid-cols-3', className)}>
      <div className="min-w-0 space-y-0.5">
        <dt className="text-muted-foreground">{t('record.payloadHash')}</dt>
        <dd>{record.payloadHash ? <HashText hash={record.payloadHash} label={t('record.copyHash')} /> : '–'}</dd>
      </div>
      <div className="min-w-0 space-y-0.5">
        <dt className="text-muted-foreground">{t('record.correlationId')}</dt>
        <dd className="min-w-0">
          {record.correlationId ? <IdText value={record.correlationId} label={t('record.correlationId')} /> : '–'}
        </dd>
      </div>
      <div className="min-w-0 space-y-0.5">
        <dt className="text-muted-foreground">{t('record.messageId')}</dt>
        <dd className="min-w-0">
          <IdText value={record.messageId} label={t('record.messageId')} />
        </dd>
      </div>
    </dl>
  )
}

/** One message of a case: direction, type, partner, status, compact flow, identifiers, expandable step details. */
export function ExchangeRecordCard({ record, defaultOpen = false }: { record: ExchangeRecord; defaultOpen?: boolean }) {
  const { t } = useTranslation('exchange')
  const typeLabel = useMessageTypeLabel()
  const [open, setOpen] = useState(defaultOpen)
  const steps = buildTimeline(record)
  const done = steps.filter((s) => s.state === 'OK' || s.state === 'SKIPPED').length
  const partner = record.partner.name ?? record.partner.bpnl
  const directionPhrase =
    record.messageType === 'TWIN_REGISTRATION'
      ? t('direction.local', { partner })
      : `${t(`direction.${record.direction}`)} ${
          isPullRecord(record.messageType)
            ? t('direction.pullFrom', { partner })
            : record.direction === 'OUTBOUND'
              ? t('direction.to', { partner })
              : t('direction.from', { partner })
        }`
  const headingId = `exchange-record-${record.id}`

  return (
    <article
      aria-labelledby={headingId}
      data-status={record.status}
      className={cn(
        'rounded-xl border bg-card shadow-xs',
        isFailure(record.status) && 'border-(--status-failed-border)',
      )}
    >
      <Collapsible open={open} onOpenChange={setOpen}>
        <div className="space-y-4 p-4">
          <header className="flex flex-wrap items-start gap-x-3 gap-y-2">
            <DirectionIcon direction={record.direction} />
            <div className="min-w-0 flex-1 basis-[13rem] space-y-0.5">
              <h3 id={headingId} className="flex flex-wrap items-baseline gap-x-1.5 text-sm font-semibold">
                {typeLabel(record.messageType)}
                <span className="font-normal text-muted-foreground">· {directionPhrase}</span>
              </h3>
              <RecordMeta record={record} />
            </div>
            <ExchangeStatusBadge status={record.status} />
          </header>

          <StepStrip steps={steps} />
          <RecordError record={record} />
          <RecordIdentifiers record={record} className="border-t pt-3" />
        </div>
        <CollapsibleContent className="border-t bg-muted/20 px-4 py-4">
          <StepList steps={steps} grouped={isPullRecord(record.messageType)} />
        </CollapsibleContent>
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex w-full items-center justify-between gap-2 rounded-b-xl border-t px-4 py-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-muted/50 hover:text-foreground focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring"
          >
            <span>{open ? t('record.hideSteps') : t('record.showSteps')}</span>
            <span className="inline-flex items-center gap-2 tabular">
              {t('record.progress', { done, total: steps.length })}
              <ChevronDown aria-hidden="true" className={cn('size-4 transition-transform', open && 'rotate-180')} />
            </span>
          </button>
        </CollapsibleTrigger>
      </Collapsible>
    </article>
  )
}
