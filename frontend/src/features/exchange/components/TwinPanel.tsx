import { ChevronDown, Eye, Globe, TriangleAlert, Waypoints } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Skeleton } from '@/components/ui/skeleton'
import type { ApprovalCaseDetail } from '@/features/approvals/api/types'
import { useFormat } from '@/features/approvals/components/format'
import { latestSubmission, revisionExchangeMode } from '@/features/approvals/model/submissions'
import { cn } from '@/lib/utils'

import { useTwin } from '../api/hooks'
import { twinVisibility, type Twin, type TwinSubmodel } from '../model/twin'
import { IdText } from './parts'

/** Monospace value that wraps (long URNs, endpoints) with a copy button. */
function MonoValue({ value, label }: { value: string; label: string }) {
  const { t } = useTranslation('exchange')
  return (
    <span className="flex min-w-0 items-start gap-1">
      <code className="min-w-0 font-mono text-[11px] leading-5 break-all">{value}</code>
      <CopyButton value={value} label={t('record.copyValue', { label })} />
    </span>
  )
}

function Field({ label, value }: { label: string; value: string | undefined }) {
  return (
    <div className="contents">
      <dt className="pt-0.5 text-xs text-muted-foreground">{label}</dt>
      <dd className="min-w-0">{value ? <MonoValue value={value} label={label} /> : '–'}</dd>
    </div>
  )
}

function SubmodelCard({ submodel }: { submodel: TwinSubmodel }) {
  const { t } = useTranslation('exchange')
  const fmt = useFormat()
  return (
    <li className="space-y-2 rounded-lg border bg-card px-3 py-2.5" data-revision={submodel.revision}>
      <p className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5 text-sm font-medium">
        {submodel.revision !== undefined
          ? t('twin.revision', { revision: submodel.revision })
          : t('twin.revisionUnknown')}
        {submodel.registeredAt ? (
          <span className="text-xs font-normal text-muted-foreground tabular">
            {t('twin.registeredAt', { at: fmt.dateTime(submodel.registeredAt) })}
          </span>
        ) : null}
      </p>
      <dl className="grid grid-cols-1 gap-x-3 gap-y-1.5 sm:grid-cols-[minmax(9rem,auto)_minmax(0,1fr)]">
        <Field label={t('twin.submodelId')} value={submodel.id} />
        <Field label={t('twin.semanticId')} value={submodel.semanticId} />
        <Field label={t('twin.endpoint')} value={submodel.href} />
        <Field label={t('twin.subprotocol')} value={submodel.subprotocol} />
        <Field label={t('twin.subprotocolBody')} value={submodel.subprotocolBody} />
      </dl>
    </li>
  )
}

function TwinDetails({ twin }: { twin: Twin }) {
  const { t } = useTranslation('exchange')
  return (
    <div className="space-y-5">
      <section className="space-y-2" aria-labelledby="twin-asset-ids">
        <h4 id="twin-asset-ids" className="text-xs font-medium text-muted-foreground">
          {t('twin.specificAssetIds')} <span className="font-mono">(specificAssetIds)</span>
        </h4>
        <div className="relative overflow-x-auto rounded-lg border bg-card">
          <table className="w-full text-sm">
            <tbody>
              {twin.assetIds.map((a) => {
                const label = t(`twin.assetIdNames.${a.name}` as 'twin.assetIdNames.customerPartId', {
                  defaultValue: a.name,
                })
                return (
                  <tr key={`${a.name}:${a.value}`} className="border-t first:border-t-0 align-top">
                    <th scope="row" className="w-0 px-3 py-2 text-left font-normal whitespace-nowrap">
                      <span className="block text-xs">{label}</span>
                      <span className="block font-mono text-[10px] text-muted-foreground">{a.name}</span>
                    </th>
                    <td className="px-3 py-2">
                      <MonoValue value={a.value} label={label} />
                    </td>
                    <td className="hidden px-3 py-2 text-right sm:table-cell">
                      {a.visibleTo ? (
                        <span className="inline-flex items-center gap-1 font-mono text-[11px] text-muted-foreground">
                          <Eye aria-hidden="true" className="size-3" />
                          <span className="sr-only">{t('twin.visibility')}: </span>
                          {a.visibleTo}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground">
                          <Globe aria-hidden="true" className="size-3" />
                          {t('twin.public')}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="text-xs text-muted-foreground">{t('twin.visibilityHint')}</p>
      </section>
      <section className="space-y-2" aria-labelledby="twin-submodels">
        <h4 id="twin-submodels" className="text-xs font-medium text-muted-foreground">
          {t('twin.submodels')}
        </h4>
        {twin.submodels.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('twin.submodelsEmpty')}</p>
        ) : (
          <ul className="space-y-2">
            {twin.submodels.map((s) => (
              <SubmodelCard key={s.id} submodel={s} />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

/**
 * Supplier: the case's digital twin in the own registry (GET /twins/{exchangeId}) – shell id, identifiers,
 * visibility and one submodel descriptor per submitted revision. Collapsed by default (diagnostics).
 */
export function TwinPanel({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation(['exchange', 'common'])
  const fmt = useFormat()
  const query = useTwin(c.exchangeId)
  const mode = revisionExchangeMode(latestSubmission(c.submissions.filter((s) => s.status === 'SUBMITTED')))
  const [open, setOpen] = useState(false)
  const headingId = useId()

  const header = (
    <div className="flex min-w-0 flex-1 items-start gap-3">
      <span
        aria-hidden="true"
        className="flex size-8 shrink-0 items-center justify-center rounded-lg border border-participant/30 bg-participant-soft text-participant-soft-foreground"
      >
        <Waypoints className="size-4" strokeWidth={2.25} />
      </span>
      <div className="min-w-0 flex-1 space-y-0.5">
        <h3 id={headingId} className="text-sm font-semibold">
          {t('twin.title')}
        </h3>
        <p className="text-xs text-muted-foreground">{t('twin.description')}</p>
      </div>
    </div>
  )

  let body
  if (query.isPending) {
    body = <Skeleton className="h-9 w-full" />
  } else if (query.isError) {
    body = (
      <p className="flex flex-wrap items-center gap-2 text-sm">
        <TriangleAlert aria-hidden="true" className="size-4 text-(--status-failed-fg)" />
        {t('twin.loadError')}
        <Button variant="outline" size="xs" onClick={() => void query.refetch()}>
          {t('common:retry')}
        </Button>
      </p>
    )
  } else if (!query.data) {
    body = (
      <p className="rounded-md border border-dashed px-3 py-2.5 text-sm text-muted-foreground" data-twin="empty">
        <span className="font-medium text-foreground">{t('twin.empty')}</span> –{' '}
        {mode === 'PUSH' ? t('twin.emptyPush') : t('twin.emptyDescription')}
      </p>
    )
  } else {
    const twin = query.data
    const { restricted, hasPublic } = twinVisibility(twin)
    body = (
      <Collapsible open={open} onOpenChange={setOpen} className="space-y-3">
        <dl className="flex flex-wrap items-center gap-x-6 gap-y-2 text-xs">
          <div className="flex max-w-full min-w-0 items-center gap-1.5">
            <dt className="text-muted-foreground">{t('twin.shellId')}</dt>
            <dd className="min-w-0">
              <IdText value={twin.shellId} label={t('twin.shellId')} className="max-w-full sm:max-w-[22rem]" />
            </dd>
          </div>
          <div className="flex items-center gap-1.5">
            <dt className="text-muted-foreground">{t('twin.visibility')}</dt>
            <dd className="flex flex-wrap items-center gap-1">
              {restricted.map((bpnl) => (
                <code key={bpnl} className="rounded bg-muted px-1.5 py-0.5 font-mono text-[11px]">
                  {bpnl}
                </code>
              ))}
              {hasPublic ? <span>{t('twin.public')}</span> : null}
            </dd>
          </div>
          <div className="flex items-center gap-1.5">
            <dt className="text-muted-foreground">{t('twin.submodels')}</dt>
            <dd className="tabular">{twin.submodels.length}</dd>
          </div>
          {(twin.updatedAt ?? twin.registeredAt) ? (
            <div className="text-muted-foreground tabular">
              <dt className="sr-only">{t('twin.updatedAt', { at: '' })}</dt>
              <dd>{t('twin.updatedAt', { at: fmt.dateTime(twin.updatedAt ?? twin.registeredAt) })}</dd>
            </div>
          ) : null}
        </dl>
        <CollapsibleContent className="border-t pt-4">
          <TwinDetails twin={twin} />
        </CollapsibleContent>
        <CollapsibleTrigger asChild>
          <Button variant="ghost" size="sm" className="-ml-2">
            <ChevronDown aria-hidden="true" className={cn('transition-transform', open && 'rotate-180')} />
            {open ? t('twin.hide') : t('twin.show')}
          </Button>
        </CollapsibleTrigger>
      </Collapsible>
    )
  }

  return (
    <section aria-labelledby={headingId} className="space-y-3 rounded-xl border bg-card p-4 shadow-xs" data-twin-panel>
      {header}
      {body}
    </section>
  )
}
