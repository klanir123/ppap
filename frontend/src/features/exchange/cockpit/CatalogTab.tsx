import { BookOpen, ChevronRight, Loader2, RefreshCw, Search, Timer, TriangleAlert } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { EmptyState } from '@/components/EmptyState'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useFormat } from '@/features/approvals/components/format'
import { partnersApi } from '@/features/masterdata/api'

import { usePartnerCatalog } from '../api/connector-hooks'
import type { CatalogDataset, PartnerCatalog } from '../api/connector'
import { JsonViewer } from '../components/JsonViewer'
import { GlossaryTerm, IdText } from '../components/parts'
import { shortTerm } from '../model/odrl'
import { catalogError } from './catalog-error'
import { ConstraintChips } from './cockpit-parts'

/** Cockpit › Partner-Katalog: catalog of a partner's connector, requested live via DSP (AK-10-2). */
export function CatalogTab({
  partner,
  onPartnerChange,
}: {
  partner?: string
  onPartnerChange: (bpnl: string | undefined) => void
}) {
  const { t } = useTranslation(['exchange', 'common'])
  const selectId = useId()
  const partners = partnersApi.usePage({ active: true, size: 200, sort: ['name,asc'] })
  const catalog = usePartnerCatalog()
  const options = (partners.data?.content ?? []).filter((p) => p.exchangeReady)
  const requested = useRef<string | undefined>(undefined)

  // a partner preselected via URL is fetched once on entry
  useEffect(() => {
    if (partner && requested.current !== partner && !catalog.isPending) {
      requested.current = partner
      catalog.mutate(partner)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run on partner change only
  }, [partner])

  const fetchNow = () => {
    if (!partner) return
    requested.current = partner
    catalog.mutate(partner)
  }
  const result =
    catalog.data && catalog.data.partnerBpnl === partner ? catalog.data : catalog.isPending ? undefined : catalog.data
  const error = catalog.isError ? catalogError(catalog.error) : undefined

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 rounded-lg border bg-card p-4 sm:flex-row sm:items-end">
        <div className="grid flex-1 gap-1.5">
          <Label htmlFor={selectId}>{t('cockpit.catalog.partner')}</Label>
          <Select value={partner ?? ''} onValueChange={(v) => onPartnerChange(v || undefined)}>
            <SelectTrigger id={selectId} className="w-full sm:max-w-md" data-testid="catalog-partner">
              <SelectValue placeholder={t('cockpit.catalog.choosePartner')} />
            </SelectTrigger>
            <SelectContent>
              {options.map((p) => (
                <SelectItem key={p.bpnl} value={p.bpnl}>
                  {p.name} · {p.bpnl}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            {t('cockpit.catalog.hint')} <GlossaryTerm term="DSP" />
          </p>
        </div>
        <Button onClick={fetchNow} disabled={!partner || catalog.isPending} data-action="fetch-catalog">
          {catalog.isPending ? (
            <Loader2 aria-hidden="true" className="animate-spin" />
          ) : result ? (
            <RefreshCw aria-hidden="true" />
          ) : (
            <Search aria-hidden="true" />
          )}
          {result ? t('cockpit.catalog.refetch') : t('cockpit.catalog.fetch')}
        </Button>
      </div>

      <div aria-live="polite" aria-busy={catalog.isPending || undefined}>
        {catalog.isPending ? (
          <div className="space-y-3">
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 aria-hidden="true" className="size-4 animate-spin" />
              {t('cockpit.catalog.loading')}
            </p>
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : error ? (
          <Alert variant="destructive" data-testid="catalog-error">
            <TriangleAlert aria-hidden="true" />
            <AlertTitle>{t('cockpit.catalog.failed', { title: error.title })}</AlertTitle>
            <AlertDescription className="space-y-2">
              {error.message ? <p className="font-mono text-xs break-words">{error.message}</p> : null}
              <p>{t('cockpit.catalog.failedHint')}</p>
              {error.correlationId ? (
                <p className="inline-flex items-center gap-1 text-xs">
                  {t('cockpit.catalog.correlationId')}: <code className="font-mono">{error.correlationId}</code>
                  <CopyButton value={error.correlationId} label={t('cockpit.log.copyCorrelation')} />
                </p>
              ) : null}
            </AlertDescription>
          </Alert>
        ) : result ? (
          <CatalogResult catalog={result} />
        ) : (
          <EmptyState
            icon={BookOpen}
            title={t('cockpit.catalog.emptyTitle')}
            description={t('cockpit.catalog.emptyDescription')}
          />
        )}
      </div>
    </div>
  )
}

export function CatalogResult({ catalog }: { catalog: PartnerCatalog }) {
  const { t } = useTranslation('exchange')
  const fmt = useFormat()
  return (
    <div className="space-y-4" data-testid="catalog-result">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 rounded-lg border bg-muted/30 px-4 py-3 text-sm">
        <span className="font-medium">{catalog.partnerName ?? catalog.partnerBpnl}</span>
        <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground" data-testid="catalog-duration">
          <Timer aria-hidden="true" className="size-3.5" />
          {t('cockpit.catalog.duration', { ms: fmt.number(catalog.durationMs) })}
        </span>
        <span className="text-xs text-muted-foreground">
          {t('cockpit.catalog.datasets', { count: catalog.datasets.length })}
        </span>
        {catalog.participantId ? (
          <span className="min-w-0 text-xs">
            <IdText value={catalog.participantId} label={t('cockpit.overview.participantId')} />
          </span>
        ) : null}
        {catalog.dspEndpoint ? (
          <span className="min-w-0 text-xs">
            <IdText value={catalog.dspEndpoint} label={t('cockpit.overview.dspEndpoint')} />
          </span>
        ) : null}
      </div>
      {catalog.datasets.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={t('cockpit.catalog.noDatasets')}
          description={t('cockpit.catalog.noDatasetsHint')}
        />
      ) : (
        <ul className="grid gap-3 lg:grid-cols-2">
          {catalog.datasets.map((d) => (
            <li key={d.assetId}>
              <DatasetCard dataset={d} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function DatasetCard({ dataset: d }: { dataset: CatalogDataset }) {
  const { t } = useTranslation('exchange')
  return (
    <article
      className="h-full space-y-3 rounded-lg border bg-card p-4"
      aria-label={d.assetId}
      data-testid="catalog-dataset"
    >
      <div className="space-y-1">
        <h3 className="font-mono text-sm font-semibold break-all">{d.assetId}</h3>
        {d.type ? (
          <p className="font-mono text-xs text-muted-foreground" title={d.type}>
            dct:type · {shortTerm(d.type)}
          </p>
        ) : null}
        {d.description ? <p className="text-xs text-muted-foreground">{d.description}</p> : null}
      </div>
      <div className="space-y-2">
        <p className="text-xs font-medium text-muted-foreground">
          {t('cockpit.catalog.offers', { count: d.offers.length })}
        </p>
        {d.offers.map((offer) => (
          <details key={offer.offerId} className="group rounded-md border bg-muted/20">
            <summary className="flex cursor-pointer list-none items-start gap-2 rounded-md px-2.5 py-2 hover:bg-muted/40 focus-visible:outline-2 focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
              <ChevronRight
                aria-hidden="true"
                className="mt-0.5 size-3.5 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
              />
              <span className="min-w-0 flex-1 space-y-1.5">
                <span className="block truncate font-mono text-[11px] text-muted-foreground" title={offer.offerId}>
                  {offer.offerId}
                </span>
                <ConstraintChips policy={offer.policy} />
              </span>
            </summary>
            <div className="border-t p-2">
              <JsonViewer
                value={offer.policy}
                label={t('cockpit.catalog.policyJson')}
                copyLabel={t('cockpit.catalog.copyPolicy')}
              />
            </div>
          </details>
        ))}
      </div>
    </article>
  )
}
