import type { ColumnDef } from '@tanstack/react-table'
import { getRouteApi, Link } from '@tanstack/react-router'
import { Cable, Eye } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable } from '@/components/data-table'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { HashText, PartnerText } from '@/features/approvals/components/badges'
import { useFormat } from '@/features/approvals/components/format'
import { partnersApi } from '@/features/masterdata/api'

import { useExchangeMessages } from '../api/hooks'
import {
  EXCHANGE_DIRECTIONS,
  EXCHANGE_STATUSES,
  LOCAL_KINDS,
  NOTIFICATION_TYPES,
  PULL_KINDS,
  type ExchangeRecord,
} from '../api/types'
import { RetryButton } from '../components/ExchangeRecordCard'
import { DirectionLabel, ExchangeStatusBadge } from '../components/parts'
import { useMessageTypeLabel } from '../components/use-labels'
import { hasMessageFilters, toMessageParams, type CockpitSearch } from './cockpit-search'
import { MessageDrawer } from './MessageDrawer'

const routeApi = getRouteApi('/cockpit')
const ALL = '__all__'

/** Cockpit › Nachrichten: message table (filters in the URL) with detail drawer. */
export function MessagesTab() {
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
            update({ direction: undefined, status: undefined, type: undefined, partner: undefined, page: undefined })
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
  const typeLabel = useMessageTypeLabel()
  const partners = partnersApi.usePage({ active: true, size: 200, sort: ['name,asc'] })
  const groups = [
    { key: 'groupNotifications', types: NOTIFICATION_TYPES },
    { key: 'groupPulls', types: PULL_KINDS },
    { key: 'groupLocal', types: LOCAL_KINDS },
  ] as const
  return (
    <>
      <Select
        value={search.type ?? ALL}
        onValueChange={(v) => onChange({ type: v === ALL ? undefined : (v as CockpitSearch['type']), page: undefined })}
      >
        <SelectTrigger size="sm" className="w-full sm:w-52" aria-label={t('cockpit.messages.filters.messageType')}>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{t('cockpit.messages.filters.allTypes')}</SelectItem>
          {groups.map((group) => (
            <SelectGroup key={group.key}>
              <SelectLabel>{t(`cockpit.messages.filters.${group.key}`)}</SelectLabel>
              {group.types.map((type) => (
                <SelectItem key={type} value={type}>
                  {typeLabel(type)}
                </SelectItem>
              ))}
            </SelectGroup>
          ))}
        </SelectContent>
      </Select>
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
