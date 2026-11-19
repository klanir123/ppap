import { Cable, TriangleAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import type { ApprovalCaseDetail } from '@/features/approvals/api/types'

import { useCaseExchange } from '../api/hooks'
import { isFailure, sortNewestFirst } from '../model/status'
import { ExchangeRecordCard } from './ExchangeRecordCard'
import { GlossaryTerm, IdText } from './parts'

/** Case detail › "Datenaustausch": all EDC messages of the case, newest first. */
export function ExchangeTab({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation(['exchange', 'common'])
  const query = useCaseExchange(c.id)

  const intro = (
    <div className="space-y-2.5 rounded-lg border bg-muted/30 px-4 py-3">
      <p className="text-sm text-muted-foreground">{t('tab.intro')}</p>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-xs text-muted-foreground">{t('tab.glossary')}:</span>
          <GlossaryTerm term="EDC" />
          <GlossaryTerm term="DSP" />
          <GlossaryTerm term="EDR" />
        </div>
        <span className="flex min-w-0 items-center gap-1.5 text-xs text-muted-foreground sm:ml-auto">
          {t('tab.exchangeId')}
          <IdText value={c.exchangeId} label={t('tab.exchangeId')} className="max-w-[18rem]" />
        </span>
      </div>
    </div>
  )

  if (query.isPending) {
    return (
      <div className="space-y-4" aria-busy="true">
        {intro}
        <Skeleton className="h-52 w-full rounded-xl" />
        <Skeleton className="h-52 w-full rounded-xl" />
      </div>
    )
  }
  if (query.isError) {
    return (
      <div className="space-y-4">
        {intro}
        <EmptyState
          icon={TriangleAlert}
          title={t('tab.loadError')}
          action={
            <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
              {t('common:retry')}
            </Button>
          }
        />
      </div>
    )
  }
  const records = sortNewestFirst(query.data)
  if (records.length === 0) {
    const description =
      c.source === 'MANUAL'
        ? t('tab.emptyManual')
        : c.source === 'LEGACY_IMPORT'
          ? t('tab.emptyLegacy')
          : t('tab.emptyDescription')
    return (
      <div className="space-y-4">
        {intro}
        <EmptyState icon={Cable} title={t('tab.empty')} description={description} />
      </div>
    )
  }
  return (
    <div className="space-y-4">
      {intro}
      <ul className="space-y-3" aria-label={t('tab.list')}>
        {records.map((record, index) => (
          <li key={record.id}>
            <ExchangeRecordCard record={record} defaultOpen={index === 0 && isFailure(record.status)} />
          </li>
        ))}
      </ul>
    </div>
  )
}
