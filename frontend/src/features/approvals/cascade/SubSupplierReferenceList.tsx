import { EyeOff, Network } from 'lucide-react'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/utils'

import type { SubSupplierReference } from '../api'
import { CaseStatusBadge, HashText } from '../components/badges'
import { useFormat } from '../components/format'

/**
 * Received sub-supplier references of a revision (customer side, UC-09 step 5): who approved which sub-part,
 * with result, date and decision hash. Documents of sub-suppliers are never passed on (AK-09-2).
 */
export function SubSupplierReferenceList({
  references,
  className,
  compact,
}: {
  references: readonly SubSupplierReference[] | null | undefined
  className?: string
  compact?: boolean
}) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  const headingId = useId()
  if (!references?.length) return null
  return (
    <section
      aria-labelledby={headingId}
      className={cn('space-y-3 rounded-lg border bg-card p-4', className)}
      data-testid="sub-supplier-reference-list"
    >
      <div className="space-y-1">
        <h3 id={headingId} className="flex items-center gap-2 text-sm font-semibold">
          <Network aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('cascade.received.title', { count: references.length })}
        </h3>
        <p className="flex items-start gap-1.5 text-xs text-muted-foreground" data-testid="sub-supplier-no-documents">
          <EyeOff aria-hidden="true" className="mt-px size-3.5 shrink-0" />
          {t('cascade.received.noDocuments')}
        </p>
      </div>
      <ul className={cn('grid gap-2', !compact && 'md:grid-cols-2')}>
        {references.map((r) => (
          <li
            key={`${r.exchangeId}:${r.revision}`}
            className="space-y-2 rounded-md border bg-muted/20 px-3 py-2.5"
            data-testid="sub-supplier-reference"
          >
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{r.subSupplierName ?? r.subSupplierBpnl}</p>
                <p className="font-mono text-[11px] text-muted-foreground">{r.subSupplierBpnl}</p>
              </div>
              <CaseStatusBadge status={r.decisionResult} size="sm" />
            </div>
            <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
              <dt className="text-muted-foreground">{t('cascade.received.part')}</dt>
              <dd className="min-w-0 truncate">
                <span className="font-mono">{r.partNumber}</span>
                {r.partName ? ` · ${r.partName}` : ''}
                {r.supplierPartNumber ? (
                  <span className="text-muted-foreground">
                    {' '}
                    ({t('cascade.received.supplierPartNumber', { number: r.supplierPartNumber })})
                  </span>
                ) : null}
              </dd>
              <dt className="text-muted-foreground">{t('cascade.received.decision')}</dt>
              <dd>
                {fmt.date(r.decidedAt)} · {t('revisions.revisionTitle', { revision: r.revision })} ·{' '}
                <span className="font-mono">{r.caseNumber}</span>
              </dd>
              {r.conditionDueDate ? (
                <>
                  <dt className="text-muted-foreground">{t('cascade.received.conditionDue')}</dt>
                  <dd>{fmt.date(r.conditionDueDate)}</dd>
                </>
              ) : null}
              <dt className="self-center text-muted-foreground">{t('cascade.received.hash')}</dt>
              <dd>
                <HashText hash={r.decisionHash} label={t('cascade.references.copyHash')} />
              </dd>
            </dl>
          </li>
        ))}
      </ul>
    </section>
  )
}
