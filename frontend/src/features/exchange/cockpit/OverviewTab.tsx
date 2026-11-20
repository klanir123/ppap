import {
  ArrowRight,
  Cable,
  FileSignature,
  Handshake,
  Inbox,
  Layers,
  Network,
  Package,
  Send,
  ShieldCheck,
  Truck,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useFormat } from '@/features/approvals/components/format'
import { isHealthStatus } from '@/lib/status'
import { cn } from '@/lib/utils'

import { useConnectorOverview } from '../api/connector-hooks'
import type { ComponentState } from '../api/connector'
import { GlossaryTerm, IdText, type GlossaryKey } from '../components/parts'
import type { CockpitSearch, CockpitTab } from './cockpit-search'
import { StatCard } from './cockpit-parts'

export function HealthBadge({ status, size }: { status: ComponentState; size?: 'sm' | 'md' }) {
  return <StatusBadge kind="health" status={isHealthStatus(status) ? status : 'UNKNOWN'} size={size} />
}

/** Cockpit › Übersicht: EDC / DTR / identity, provisioning, counts and outbox / inbox. */
export function OverviewTab({ onNavigate }: { onNavigate: (patch: Partial<CockpitSearch>) => void }) {
  const { t } = useTranslation(['exchange', 'common'])
  const fmt = useFormat()
  const query = useConnectorOverview()

  if (query.isPending) {
    return (
      <div className="space-y-4" aria-busy="true">
        <div className="grid gap-4 md:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-36 w-full rounded-xl" />
          ))}
        </div>
        <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-5">
          {[0, 1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-24 w-full rounded-xl" />
          ))}
        </div>
      </div>
    )
  }
  if (query.isError) {
    return (
      <Card className="py-4">
        <CardContent className="flex flex-wrap items-center gap-3 px-4 text-sm">
          <TriangleAlert aria-hidden="true" className="size-4 text-(--status-failed-fg)" />
          {t('cockpit.status.loadError')}
          <Button variant="outline" size="sm" className="ml-auto" onClick={() => void query.refetch()}>
            {t('common:retry')}
          </Button>
        </CardContent>
      </Card>
    )
  }
  const o = query.data
  const counts = o.counts ?? { assets: 0, policies: 0, contractDefinitions: 0, negotiations: 0, transfers: 0 }
  const go = (tab: CockpitTab, patch: Partial<CockpitSearch> = {}) => onNavigate({ tab, ...patch })

  return (
    <div className="space-y-6" data-testid="cockpit-overview">
      <div className="grid gap-4 md:grid-cols-3">
        <StatCard
          icon={Cable}
          title={<GlossaryLabel term="EDC" label={t('cockpit.overview.edc')} />}
          aside={<HealthBadge status={o.edc.status} size="sm" />}
        >
          <dl className="grid gap-1.5 text-xs">
            <Row label={t('cockpit.overview.participantId')}>
              {o.edc.participantId ? (
                <IdText value={o.edc.participantId} label={t('cockpit.overview.participantId')} />
              ) : (
                '–'
              )}
            </Row>
            <Row label={t('cockpit.overview.dspEndpoint')}>
              {o.edc.dspEndpoint ? <IdText value={o.edc.dspEndpoint} label={t('cockpit.overview.dspEndpoint')} /> : '–'}
            </Row>
            {o.edc.version ? (
              <Row label={t('cockpit.overview.version')}>
                <span className="font-mono text-[11px]">{o.edc.version}</span>
              </Row>
            ) : null}
            {o.edc.responseMs != null ? (
              <Row label={t('cockpit.overview.responseTime')}>
                <span className="tabular">{t('cockpit.overview.ms', { ms: fmt.number(o.edc.responseMs) })}</span>
              </Row>
            ) : null}
          </dl>
          {o.edc.error ? (
            <p className="font-mono text-[11px] break-words text-(--status-failed-fg)">{o.edc.error}</p>
          ) : null}
        </StatCard>
        <StatCard
          icon={Network}
          title={<GlossaryLabel term="DTR" label={t('cockpit.overview.dtr')} />}
          aside={o.dtr ? <HealthBadge status={o.dtr.status} size="sm" /> : null}
        >
          {o.dtr ? (
            <dl className="grid gap-1.5 text-xs">
              <Row label={t('cockpit.overview.url')}>
                {o.dtr.url ? <IdText value={o.dtr.url} label={t('cockpit.overview.url')} /> : '–'}
              </Row>
            </dl>
          ) : (
            <p className="text-xs text-muted-foreground">{t('cockpit.overview.noDtr')}</p>
          )}
          <p className="text-xs text-muted-foreground">{t('cockpit.overview.dtrHint')}</p>
        </StatCard>
        <StatCard
          icon={Layers}
          title={t('cockpit.status.provisioning')}
          aside={<StatusBadge kind="validation" status={o.provisioning.ok ? 'OK' : 'ERROR'} size="sm" />}
        >
          <p className="text-xs text-muted-foreground">
            {o.provisioning.ok ? t('cockpit.status.provisioningOk') : t('cockpit.status.provisioningFailed')}
            {o.provisioning.lastRun
              ? ` · ${t('cockpit.status.lastRun', { at: fmt.dateTime(o.provisioning.lastRun) })}`
              : ''}
          </p>
          {o.provisioning.error ? (
            <p className="font-mono text-[11px] break-words text-(--status-failed-fg)">{o.provisioning.error}</p>
          ) : null}
          {o.provisioning.assets?.length ? (
            <ul className="flex flex-wrap gap-1">
              {o.provisioning.assets.map((a) => (
                <li key={a} className="rounded border bg-muted/50 px-1.5 py-0.5 font-mono text-[10px]">
                  {a}
                </li>
              ))}
            </ul>
          ) : null}
          <p className="text-xs text-muted-foreground">{t('cockpit.overview.provisioningHint')}</p>
        </StatCard>
      </div>

      <section aria-labelledby="cockpit-counts" className="space-y-3">
        <h2 id="cockpit-counts" className="text-sm font-semibold">
          {t('cockpit.overview.countsTitle')}
        </h2>
        <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-5">
          <CountTile
            icon={Package}
            term="ASSET"
            label={t('cockpit.overview.counts.assets')}
            value={counts.assets}
            onClick={() => go('offers')}
          />
          <CountTile
            icon={ShieldCheck}
            term="POLICY"
            label={t('cockpit.overview.counts.policies')}
            value={counts.policies}
            onClick={() => go('policies')}
          />
          <CountTile
            icon={FileSignature}
            term="CONTRACT_DEFINITION"
            label={t('cockpit.overview.counts.contractDefinitions')}
            value={counts.contractDefinitions}
            onClick={() => go('policies')}
          />
          <CountTile
            icon={Handshake}
            term="NEGOTIATION"
            label={t('cockpit.overview.counts.negotiations')}
            value={counts.negotiations}
            onClick={() => go('processes')}
          />
          <CountTile
            icon={Truck}
            term="TRANSFER"
            label={t('cockpit.overview.counts.transfers')}
            value={counts.transfers}
            onClick={() => go('processes')}
          />
        </div>
      </section>

      <section aria-labelledby="cockpit-queues" className="space-y-3">
        <h2 id="cockpit-queues" className="text-sm font-semibold">
          {t('cockpit.overview.queuesTitle')}
        </h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <CountTile
            icon={Send}
            label={t('cockpit.overview.outboxPending')}
            value={o.outbox.pending}
            onClick={() => go('messages', { direction: 'OUTBOUND', status: 'PENDING' })}
          />
          <CountTile
            icon={Send}
            label={t('cockpit.overview.outboxFailed')}
            value={o.outbox.failed}
            alert
            onClick={() => go('messages', { direction: 'OUTBOUND', status: 'FAILED' })}
          />
          <CountTile
            icon={Inbox}
            label={t('cockpit.overview.inboxRejected')}
            value={o.inbox.rejected}
            alert
            onClick={() => go('messages', { direction: 'INBOUND', status: 'REJECTED' })}
          />
        </div>
      </section>
    </div>
  )
}

function GlossaryLabel({ term, label }: { term: GlossaryKey; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {label}
      <GlossaryTerm term={term} />
    </span>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_minmax(0,1fr)] items-center gap-2">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  )
}

function CountTile({
  icon: Icon,
  label,
  value,
  term,
  alert,
  onClick,
}: {
  icon: LucideIcon
  label: string
  value: number
  term?: GlossaryKey
  alert?: boolean
  onClick: () => void
}) {
  const { t } = useTranslation('exchange')
  return (
    <div className="group relative rounded-xl border bg-card p-4 transition-colors hover:border-foreground/20">
      <div className="flex items-center justify-between gap-2">
        <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
          <Icon aria-hidden="true" className="size-4" />
          {label}
        </p>
        {term ? (
          <span className="relative z-10">
            <GlossaryTerm term={term} compact />
          </span>
        ) : null}
      </div>
      <div className="mt-2 flex items-end justify-between gap-2">
        <span
          className={cn(
            'text-2xl font-semibold tracking-tight tabular',
            alert && value > 0 && 'text-(--status-failed-fg)',
          )}
        >
          {value < 0 ? '–' : value}
        </span>
        <button
          type="button"
          onClick={onClick}
          className="inline-flex items-center gap-1 rounded-sm text-xs text-muted-foreground after:absolute after:inset-0 after:rounded-xl hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
        >
          {t('cockpit.overview.show')}
          <span className="sr-only">: {label}</span>
          <ArrowRight aria-hidden="true" className="size-3" />
        </button>
      </div>
    </div>
  )
}
