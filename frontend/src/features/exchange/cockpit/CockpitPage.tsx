import type { ColumnDef } from '@tanstack/react-table'
import { getRouteApi, Link } from '@tanstack/react-router'
import { Cable, Eye, Inbox, Layers, Send, TriangleAlert, type LucideIcon } from 'lucide-react'
import { useMemo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable } from '@/components/data-table'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { HashText, PartnerText } from '@/features/approvals/components/badges'
import { useFormat } from '@/features/approvals/components/format'
import { partnersApi } from '@/features/masterdata/api'
import { cn } from '@/lib/utils'

import { useExchangeMessages, useExchangeOverview } from '../api/hooks'
import { EXCHANGE_DIRECTIONS, EXCHANGE_STATUSES, type ExchangeOverview, type ExchangeRecord } from '../api/types'
import { RetryButton } from '../components/ExchangeRecordCard'
import { DirectionLabel, ExchangeStatusBadge } from '../components/parts'
import { hasMessageFilters, toMessageParams, type CockpitSearch } from './cockpit-search'
import { MessageDrawer } from './MessageDrawer'
import { useMessageTypeLabel } from '../components/use-labels'

const routeApi = getRouteApi('/cockpit')
const ALL = '__all__'

/** Connector cockpit (first version): EDC/provisioning/outbox/inbox status + message table with detail drawer. */
export function CockpitPage() {
  const { t } = useTranslation('exchange')
  const typeLabel = useMessageTypeLabel()
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const params = toMessageParams(search)
  const messages = useExchangeMessages(params)
  const fmt = useFormat()

  const update = (patch: Partial<CockpitSearch>) =>
    void navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true })
  const open = (id: string | undefined) => update({ message: id })

  const columns = useMemo<ColumnDef<ExchangeRecord, unknown>[]>(
    () => [
      {
        id: 'createdAt',
        header: t('cockpit.messages.columns.createdAt'),
        meta: { label: t('cockpit.messages.columns.createdAt'), hideable: false, sortField: false },
        cell: ({ row }) => (
          <time dateTime={row.original.createdAt} className="text-xs whitespace-nowrap tabular">
            {fmt.dateTime(row.original.createdAt)}
          </time>
        ),
      },
      {
        id: 'messageType',
        header: t('cockpit.messages.columns.messageType'),
        meta: { label: t('cockpit.messages.columns.messageType'), hideable: false, sortField: false },
        cell: ({ row }) => (
          <button
            type="button"
            onClick={() => open(row.original.id)}
            className="rounded-sm text-left text-sm font-medium underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {typeLabel(row.original.messageType)}
          </button>
        ),
      },
      {
        id: 'case',
        header: t('cockpit.messages.columns.case'),
        meta: { label: t('cockpit.messages.columns.case'), sortField: false },
        cell: ({ row }) =>
          row.original.caseId ? (
            <Link
              to="/approvals/$caseId"
              params={{ caseId: row.original.caseId }}
              search={{ tab: 'exchange' }}
              className="rounded-sm font-mono text-xs whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            >
              {row.original.caseNumber ?? row.original.caseId.slice(0, 8)}
            </Link>
          ) : (
            '–'
          ),
      },
      {
        id: 'direction',
        header: t('cockpit.messages.columns.direction'),
        meta: { label: t('cockpit.messages.columns.direction'), sortField: false },
        cell: ({ row }) => <DirectionLabel direction={row.original.direction} />,
      },
      {
        id: 'partner',
        header: t('cockpit.messages.columns.partner'),
        meta: { label: t('cockpit.messages.columns.partner'), sortField: false },
        cell: ({ row }) => (
          <PartnerText name={row.original.partner.name ?? undefined} bpnl={row.original.partner.bpnl} />
        ),
      },
      {
        id: 'status',
        header: t('cockpit.messages.columns.status'),
        meta: { label: t('cockpit.messages.columns.status'), hideable: false, sortField: false },
        cell: ({ row }) => (
          <div className="flex flex-wrap items-center gap-2">
            <ExchangeStatusBadge status={row.original.status} size="sm" />
            <RetryButton record={row.original} size="xs" />
          </div>
        ),
      },
      {
        id: 'attempts',
        header: t('cockpit.messages.columns.attempts'),
        meta: {
          label: t('cockpit.messages.columns.attempts'),
          sortField: false,
          className: 'text-right tabular',
          headerClassName: 'text-right',
        },
        cell: ({ row }) => row.original.attempts,
      },
      {
        id: 'payloadHash',
        header: t('cockpit.messages.columns.payloadHash'),
        meta: { label: t('cockpit.messages.columns.payloadHash'), sortField: false },
        cell: ({ row }) => (row.original.payloadHash ? <HashText hash={row.original.payloadHash} /> : '–'),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `open` only navigates
    [t, fmt.lng],
  )

  const selected = messages.data?.content.find((r) => r.id === search.message)

  return (
    <div className="space-y-6">
      <PageHeader title={t('cockpit.title')} description={t('cockpit.description')} />
      <OverviewCards />

      <section className="space-y-3" aria-labelledby="cockpit-messages">
        <h2 id="cockpit-messages" className="text-base font-semibold tracking-tight">
          {t('cockpit.messages.title')}
        </h2>
        <DataTable<ExchangeRecord>
          id="exchange-messages"
          label={t('cockpit.messages.title')}
          columns={columns}
          page={messages.data}
          state={{ page: params.page ?? 0, size: params.size! }}
          onStateChange={(patch) => update({ page: patch.page || undefined, size: patch.size })}
          search={false}
          activeFilter={false}
          filters={<MessageFilters search={search} onChange={update} />}
          filtered={hasMessageFilters(search)}
          onResetFilters={() =>
            update({ direction: undefined, status: undefined, partner: undefined, page: undefined })
          }
          isLoading={messages.isPending}
          isFetching={messages.isFetching}
          error={messages.error}
          onRetry={() => void messages.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => [typeLabel(row.messageType), row.caseNumber].filter(Boolean).join(' · ')}
          rowActions={(row) => [{ label: t('cockpit.messages.open'), icon: Eye, onSelect: () => open(row.id) }]}
          emptyState={{
            icon: Cable,
            title: t('cockpit.messages.empty'),
            description: t('cockpit.messages.emptyDescription'),
          }}
        />
      </section>

      <MessageDrawer id={search.message} fallback={selected} onClose={() => open(undefined)} />
    </div>
  )
}

function MessageFilters({
  search,
  onChange,
}: {
  search: CockpitSearch
  onChange: (p: Partial<CockpitSearch>) => void
}) {
  const { t } = useTranslation(['exchange', 'status'])
  const partners = partnersApi.usePage({ active: true, size: 200, sort: ['name,asc'] })
  return (
    <>
      <Select
        value={search.direction ?? ALL}
        onValueChange={(v) =>
          onChange({ direction: v === ALL ? undefined : (v as CockpitSearch['direction']), page: undefined })
        }
      >
        <SelectTrigger size="sm" className="w-full sm:w-44" aria-label={t('cockpit.messages.filters.direction')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('cockpit.messages.filters.allDirections')}</SelectItem>
          {EXCHANGE_DIRECTIONS.map((d) => (
            <SelectItem key={d} value={d}>
              {t(`direction.${d}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={search.status ?? ALL}
        onValueChange={(v) =>
          onChange({ status: v === ALL ? undefined : (v as CockpitSearch['status']), page: undefined })
        }
      >
        <SelectTrigger size="sm" className="w-full sm:w-44" aria-label={t('cockpit.messages.filters.status')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('cockpit.messages.filters.allStatuses')}</SelectItem>
          {EXCHANGE_STATUSES.map((s) => (
            <SelectItem key={s} value={s}>
              {t(`status:exchange.${s}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={search.partner ?? ALL}
        onValueChange={(v) => onChange({ partner: v === ALL ? undefined : v, page: undefined })}
      >
        <SelectTrigger size="sm" className="w-full sm:w-48" aria-label={t('cockpit.messages.filters.partner')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('cockpit.messages.filters.allPartners')}</SelectItem>
          {(partners.data?.content ?? []).map((p) => (
            <SelectItem key={p.bpnl} value={p.bpnl}>
              {p.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </>
  )
}

function StatCard({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children: ReactNode }) {
  return (
    <Card className="gap-0 py-4">
      <CardContent className="space-y-2.5 px-4">
        <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
          <Icon aria-hidden="true" className="size-4" />
          {title}
        </p>
        {children}
      </CardContent>
    </Card>
  )
}

function Count({ value, label, alert }: { value: number; label: string; alert?: boolean }) {
  return (
    <span className="flex items-baseline gap-1.5">
      <span
        className={cn(
          'text-2xl font-semibold tracking-tight tabular',
          alert && value > 0 && 'text-(--status-failed-fg)',
        )}
      >
        {value}
      </span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </span>
  )
}

function OverviewCards() {
  const { t } = useTranslation(['exchange', 'common'])
  const fmt = useFormat()
  const query = useExchangeOverview()
  if (query.isPending) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-busy="true">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-28 w-full rounded-xl" />
        ))}
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
  const s: ExchangeOverview = query.data
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <StatCard icon={Cable} title={t('cockpit.status.edc')}>
        <StatusBadge kind="health" status={s.edc === 'UP' ? 'UP' : 'DOWN'} />
        <p className="text-xs text-muted-foreground">
          {s.edc === 'UP' ? t('cockpit.status.up') : t('cockpit.status.down')}
        </p>
        {s.edcError ? (
          <p className="font-mono text-[11px] break-words text-(--status-failed-fg)">{s.edcError}</p>
        ) : null}
      </StatCard>
      <StatCard icon={Layers} title={t('cockpit.status.provisioning')}>
        <StatusBadge kind="validation" status={s.provisioning.ok ? 'OK' : 'ERROR'} />
        <p className="text-xs text-muted-foreground">
          {s.provisioning.ok ? t('cockpit.status.provisioningOk') : t('cockpit.status.provisioningFailed')}
          {' · '}
          {t('cockpit.status.assets', { count: s.provisioning.assets.length })}
          {s.provisioning.lastRun
            ? ` · ${t('cockpit.status.lastRun', { at: fmt.dateTime(s.provisioning.lastRun) })}`
            : ''}
        </p>
        {s.provisioning.error ? (
          <p className="font-mono text-[11px] break-words text-(--status-failed-fg)">{s.provisioning.error}</p>
        ) : null}
        {s.provisioning.assets.length ? (
          <ul className="flex flex-wrap gap-1">
            {s.provisioning.assets.map((a) => (
              <li key={a} className="rounded border bg-muted/50 px-1.5 py-0.5 font-mono text-[10px]">
                {a}
              </li>
            ))}
          </ul>
        ) : null}
      </StatCard>
      <StatCard icon={Send} title={t('cockpit.status.outbox')}>
        <div className="flex flex-wrap gap-x-5 gap-y-1">
          <Count value={s.outbox.pending} label={t('cockpit.status.pending')} />
          <Count value={s.outbox.failed} label={t('cockpit.status.failed')} alert />
        </div>
      </StatCard>
      <StatCard icon={Inbox} title={t('cockpit.status.inbox')}>
        <Count value={s.inbox.rejected} label={t('cockpit.status.rejected')} alert />
      </StatCard>
    </div>
  )
}
