import { Link } from '@tanstack/react-router'
import { ArrowRight, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'

import { useExchangeMessage } from '../api/hooks'
import type { ExchangeRecord } from '../api/types'
import { RecordError, RecordIdentifiers, RecordMeta } from '../components/ExchangeRecordCard'
import { JsonViewer } from '../components/JsonViewer'
import { DirectionIcon, ExchangeStatusBadge } from '../components/parts'
import { StepList } from '../components/Steps'
import { isPullRecord } from '../model/status'
import { buildTimeline } from '../model/steps'
import { useMessageTypeLabel } from '../components/use-labels'

/** Cockpit message detail: header, error + retry, identifiers, steps and payload JSON. */
export function MessageDrawer({
  id,
  fallback,
  onClose,
}: {
  id: string | undefined
  /** Row from the list – shown until the detail (with payload) has loaded. */
  fallback?: ExchangeRecord
  onClose: () => void
}) {
  const { t } = useTranslation(['exchange', 'common'])
  const typeLabel = useMessageTypeLabel()
  const query = useExchangeMessage(id)
  const record = query.data?.record ?? fallback

  return (
    <Sheet open={Boolean(id)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-2xl">
        <SheetHeader className="border-b">
          {record ? (
            <div className="flex items-start gap-3 pr-8">
              <DirectionIcon direction={record.direction} />
              <div className="min-w-0 flex-1 space-y-0.5">
                <SheetTitle className="flex flex-wrap items-center gap-2">
                  {typeLabel(record.messageType)}
                  <ExchangeStatusBadge status={record.status} size="sm" />
                </SheetTitle>
                <SheetDescription>
                  {t(`direction.${record.direction}`)}{' '}
                  {record.direction === 'OUTBOUND'
                    ? t('direction.to', { partner: record.partner.name ?? record.partner.bpnl })
                    : t('direction.from', { partner: record.partner.name ?? record.partner.bpnl })}
                </SheetDescription>
              </div>
            </div>
          ) : (
            <>
              <SheetTitle>{t('cockpit.drawer.title')}</SheetTitle>
              <SheetDescription>{t('cockpit.drawer.description')}</SheetDescription>
            </>
          )}
        </SheetHeader>

        {!record && query.isPending ? (
          <div className="space-y-3 p-4" aria-busy="true">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
        ) : !record ? (
          <div className="p-4">
            <EmptyState icon={TriangleAlert} title={t('cockpit.drawer.loadError')} />
          </div>
        ) : (
          <div className="space-y-6 p-4">
            <div className="space-y-3">
              <RecordMeta record={record} />
              <RecordError record={record} />
              {record.caseId ? (
                <Button variant="outline" size="sm" asChild>
                  <Link to="/approvals/$caseId" params={{ caseId: record.caseId }} search={{ tab: 'exchange' }}>
                    {t('cockpit.drawer.openCase')}
                    <ArrowRight aria-hidden="true" />
                  </Link>
                </Button>
              ) : null}
            </div>
            <section className="space-y-2">
              <h3 className="text-sm font-medium">{t('cockpit.drawer.identifiers')}</h3>
              <RecordIdentifiers record={record} className="sm:grid-cols-1" />
            </section>
            <section className="space-y-3">
              <h3 className="text-sm font-medium">{t('cockpit.drawer.steps')}</h3>
              <StepList steps={buildTimeline(record)} grouped={isPullRecord(record.messageType)} />
            </section>
            <section className="space-y-2">
              <h3 className="text-sm font-medium">{t('cockpit.drawer.payload')}</h3>
              {query.isPending ? (
                <Skeleton className="h-40 w-full" />
              ) : query.data?.payload !== undefined && query.data.payload !== null ? (
                <JsonViewer
                  value={query.data.payload}
                  label={t('cockpit.drawer.payload')}
                  copyLabel={t('cockpit.drawer.copyPayload')}
                />
              ) : (
                <p className="text-sm text-muted-foreground">{t('cockpit.drawer.payloadUnavailable')}</p>
              )}
            </section>
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}
