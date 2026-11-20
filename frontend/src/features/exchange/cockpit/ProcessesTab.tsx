import type { ColumnDef } from '@tanstack/react-table'
import { Handshake, Truck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable, DEFAULT_PAGE_SIZE } from '@/components/data-table'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFormat } from '@/features/approvals/components/format'

import { useNegotiations, useTransfers } from '../api/connector-hooks'
import type { ConnectorNegotiation, ConnectorTransfer, CounterParty } from '../api/connector'
import { GlossaryTerm } from '../components/parts'
import { ProcessStateBadge } from './cockpit-parts'
import { NEGOTIATION_STATES, TRANSFER_STATES } from './process-states'

const ALL = '__all__'

function CounterPartyText({ party }: { party?: CounterParty | null }) {
  if (!party) return <>–</>
  return (
    <span className="flex min-w-0 flex-col">
      <span className="truncate">{party.name ?? party.bpnl ?? party.participantId ?? '–'}</span>
      {party.bpnl || party.participantId ? (
        <span className="truncate font-mono text-[11px] text-muted-foreground" title={party.participantId ?? undefined}>
          {party.bpnl ?? party.participantId}
        </span>
      ) : null}
    </span>
  )
}

function StateFilter({
  value,
  states,
  onChange,
  label,
  allLabel,
}: {
  value?: string
  states: readonly string[]
  onChange: (v: string | undefined) => void
  label: string
  allLabel: string
}) {
  return (
    <Select value={value ?? ALL} onValueChange={(v) => onChange(v === ALL ? undefined : v)}>
      <SelectTrigger size="sm" className="w-full sm:w-48" aria-label={label}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>{allLabel}</SelectItem>
        {states.map((s) => (
          <SelectItem key={s} value={s}>
            <span className="font-mono text-xs">{s}</span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** Cockpit › Verhandlungen & Transfers (read from the EDC management API). */
export function ProcessesTab({
  negotiationState,
  transferState,
  onChange,
}: {
  negotiationState?: string
  transferState?: string
  onChange: (patch: { negotiationState?: string; transferState?: string }) => void
}) {
  const { t } = useTranslation('exchange')
  const fmt = useFormat()
  const [negPage, setNegPage] = useState({ page: 0, size: DEFAULT_PAGE_SIZE })
  const [trPage, setTrPage] = useState({ page: 0, size: DEFAULT_PAGE_SIZE })
  const negotiations = useNegotiations({ state: negotiationState })
  const transfers = useTransfers({ state: transferState })

  const common = useMemo(
    () => ({
      partner: {
        id: 'counterParty',
        header: t('cockpit.processes.columns.partner'),
        meta: { label: t('cockpit.processes.columns.partner'), sortField: false as const },
      },
      asset: {
        id: 'assetId',
        header: t('cockpit.processes.columns.asset'),
        meta: { label: t('cockpit.processes.columns.asset'), sortField: false as const },
      },
      state: {
        id: 'state',
        header: t('cockpit.processes.columns.state'),
        meta: { label: t('cockpit.processes.columns.state'), sortField: false as const, hideable: false },
      },
      time: {
        id: 'time',
        header: t('cockpit.processes.columns.time'),
        meta: { label: t('cockpit.processes.columns.time'), sortField: false as const },
      },
      type: {
        id: 'type',
        header: t('cockpit.processes.columns.type'),
        meta: { label: t('cockpit.processes.columns.type'), sortField: false as const },
      },
    }),
    [t],
  )

  const negotiationColumns = useMemo<ColumnDef<ConnectorNegotiation, unknown>[]>(
    () => [
      { ...common.time, cell: ({ row }) => <Time at={row.original.updatedAt ?? row.original.createdAt} fmt={fmt} /> },
      { ...common.partner, cell: ({ row }) => <CounterPartyText party={row.original.counterParty} /> },
      { ...common.asset, cell: ({ row }) => <Mono value={row.original.assetId} /> },
      { ...common.type, cell: ({ row }) => <TypeText type={row.original.type} /> },
      {
        ...common.state,
        cell: ({ row }) => (
          <span className="flex flex-col items-start gap-1">
            <ProcessStateBadge state={row.original.state} />
            {row.original.errorDetail ? (
              <span className="max-w-xs text-[11px] break-words text-(--status-failed-fg)">
                {row.original.errorDetail}
              </span>
            ) : null}
          </span>
        ),
      },
      {
        id: 'agreementId',
        header: t('cockpit.processes.columns.agreement'),
        meta: { label: t('cockpit.processes.columns.agreement'), sortField: false },
        cell: ({ row }) => <Mono value={row.original.agreementId} />,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fmt changes with the language only
    [common, fmt.lng],
  )

  const transferColumns = useMemo<ColumnDef<ConnectorTransfer, unknown>[]>(
    () => [
      { ...common.time, cell: ({ row }) => <Time at={row.original.updatedAt ?? row.original.createdAt} fmt={fmt} /> },
      { ...common.partner, cell: ({ row }) => <CounterPartyText party={row.original.counterParty} /> },
      { ...common.asset, cell: ({ row }) => <Mono value={row.original.assetId} /> },
      { ...common.type, cell: ({ row }) => <TypeText type={row.original.type} /> },
      {
        ...common.state,
        cell: ({ row }) => (
          <span className="flex flex-col items-start gap-1">
            <ProcessStateBadge state={row.original.state} />
            {row.original.errorDetail ? (
              <span className="max-w-xs text-[11px] break-words text-(--status-failed-fg)">
                {row.original.errorDetail}
              </span>
            ) : null}
          </span>
        ),
      },
      {
        id: 'agreementId',
        header: t('cockpit.processes.columns.agreement'),
        meta: { label: t('cockpit.processes.columns.agreement'), sortField: false },
        cell: ({ row }) => <Mono value={row.original.agreementId} />,
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fmt changes with the language only
    [common, fmt.lng],
  )

  return (
    <div className="space-y-8">
      <section className="space-y-3" aria-labelledby="cockpit-negotiations">
        <h2 id="cockpit-negotiations" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          {t('cockpit.processes.negotiations')}
          <GlossaryTerm term="NEGOTIATION" />
        </h2>
        <DataTable<ConnectorNegotiation>
          id="connector-negotiations"
          label={t('cockpit.processes.negotiations')}
          columns={negotiationColumns}
          rows={negotiations.data?.content}
          state={negPage}
          onStateChange={(patch) => setNegPage((s) => ({ ...s, ...patch }))}
          search={false}
          activeFilter={false}
          filters={
            <StateFilter
              value={negotiationState}
              states={NEGOTIATION_STATES}
              onChange={(v) => {
                setNegPage((s) => ({ ...s, page: 0 }))
                onChange({ negotiationState: v })
              }}
              label={t('cockpit.processes.stateFilter')}
              allLabel={t('cockpit.processes.allStates')}
            />
          }
          filtered={Boolean(negotiationState)}
          onResetFilters={() => onChange({ negotiationState: undefined })}
          isLoading={negotiations.isPending}
          isFetching={negotiations.isFetching}
          error={negotiations.error}
          onRetry={() => void negotiations.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.id}
          emptyState={{ icon: Handshake, title: t('cockpit.processes.emptyNegotiations') }}
        />
      </section>
      <section className="space-y-3" aria-labelledby="cockpit-transfers">
        <h2 id="cockpit-transfers" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          {t('cockpit.processes.transfers')}
          <GlossaryTerm term="TRANSFER" />
          <GlossaryTerm term="EDR" />
        </h2>
        <DataTable<ConnectorTransfer>
          id="connector-transfers"
          label={t('cockpit.processes.transfers')}
          columns={transferColumns}
          rows={transfers.data?.content}
          state={trPage}
          onStateChange={(patch) => setTrPage((s) => ({ ...s, ...patch }))}
          search={false}
          activeFilter={false}
          filters={
            <StateFilter
              value={transferState}
              states={TRANSFER_STATES}
              onChange={(v) => {
                setTrPage((s) => ({ ...s, page: 0 }))
                onChange({ transferState: v })
              }}
              label={t('cockpit.processes.stateFilter')}
              allLabel={t('cockpit.processes.allStates')}
            />
          }
          filtered={Boolean(transferState)}
          onResetFilters={() => onChange({ transferState: undefined })}
          isLoading={transfers.isPending}
          isFetching={transfers.isFetching}
          error={transfers.error}
          onRetry={() => void transfers.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.id}
          emptyState={{ icon: Truck, title: t('cockpit.processes.emptyTransfers') }}
        />
      </section>
    </div>
  )
}

function Time({ at, fmt }: { at?: string | null; fmt: ReturnType<typeof useFormat> }) {
  if (!at) return <>–</>
  return (
    <time dateTime={at} className="text-xs whitespace-nowrap tabular">
      {fmt.dateTime(at)}
    </time>
  )
}

function Mono({ value }: { value?: string | null }) {
  if (!value) return <>–</>
  return (
    <span className="block max-w-[16rem] truncate font-mono text-[11px]" title={value}>
      {value}
    </span>
  )
}

function TypeText({ type }: { type?: string | null }) {
  const { t } = useTranslation('exchange')
  if (!type) return <>–</>
  return (
    <span className="text-xs whitespace-nowrap">
      {type === 'CONSUMER' || type === 'PROVIDER' ? t(`cockpit.processes.types.${type}`) : type}
    </span>
  )
}
