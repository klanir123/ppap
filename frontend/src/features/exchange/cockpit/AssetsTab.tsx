import type { ColumnDef } from '@tanstack/react-table'
import { Eye, Package } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable, DEFAULT_PAGE_SIZE, type ListState } from '@/components/data-table'

import { useConnectorAssets, useConnectorPolicies, useContractDefinitions } from '../api/connector-hooks'
import type { ConnectorAsset } from '../api/connector'
import { GlossaryTerm } from '../components/parts'
import { shortTerm } from '../model/odrl'
import { offersForAsset } from './asset-offers'
import { ConstraintChips, ManagedBadge } from './cockpit-parts'
import { JsonSheet } from './JsonSheet'

type AssetRow = ConnectorAsset & { typeLabel: string; offers: ReturnType<typeof offersForAsset> }

/** Cockpit › Angebote: assets of the own EDC catalog with `dct:type`, managed flag and offered policy. */
export function AssetsTab({ selected, onSelect }: { selected?: string; onSelect: (id: string | undefined) => void }) {
  const { t } = useTranslation('exchange')
  const query = useConnectorAssets()
  const definitions = useContractDefinitions()
  const policies = useConnectorPolicies()
  const [state, setState] = useState<ListState>({ page: 0, size: DEFAULT_PAGE_SIZE, sort: 'id,asc' })
  const rows = useMemo<AssetRow[] | undefined>(
    () =>
      query.data?.map((a) => ({
        ...a,
        typeLabel: a.type ? shortTerm(a.type) : '',
        offers: offersForAsset(a.id, definitions.data, policies.data),
      })),
    [query.data, definitions.data, policies.data],
  )
  const columns = useMemo<ColumnDef<AssetRow, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: t('cockpit.assets.columns.id'),
        meta: { label: t('cockpit.assets.columns.id'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            onClick={() => onSelect(row.original.id)}
            className="rounded-sm text-left font-mono text-xs font-medium break-all underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {row.original.id}
          </button>
        ),
      },
      {
        id: 'typeLabel',
        accessorKey: 'typeLabel',
        header: t('cockpit.assets.columns.type'),
        meta: { label: t('cockpit.assets.columns.type') },
        cell: ({ row }) =>
          row.original.type ? (
            <span className="font-mono text-xs" title={row.original.type}>
              {row.original.typeLabel}
            </span>
          ) : (
            '–'
          ),
      },
      {
        id: 'managed',
        header: t('cockpit.assets.columns.managed'),
        meta: { label: t('cockpit.assets.columns.managed'), sortField: false },
        cell: ({ row }) => <ManagedBadge managed={row.original.managed} />,
      },
      {
        id: 'policy',
        header: t('cockpit.assets.columns.policy'),
        meta: { label: t('cockpit.assets.columns.policy'), sortField: false },
        cell: ({ row }) => {
          const offer = row.original.offers.find((o) => o.contractPolicy)
          if (offer?.contractPolicy) return <ConstraintChips policy={offer.contractPolicy.policy} />
          return row.original.offers.length ? (
            <span className="font-mono text-[11px] text-muted-foreground">
              {row.original.offers[0]!.definition.contractPolicyId}
            </span>
          ) : (
            <span className="text-xs text-muted-foreground">{t('cockpit.assets.notOffered')}</span>
          )
        },
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps -- onSelect only navigates
    [t],
  )
  const asset = rows?.find((a) => a.id === selected)

  return (
    <section className="space-y-3" aria-labelledby="cockpit-assets">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="cockpit-assets" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          {t('cockpit.assets.title')}
          <GlossaryTerm term="ASSET" />
        </h2>
        <p className="max-w-2xl text-xs text-muted-foreground">{t('cockpit.assets.description')}</p>
      </div>
      <DataTable<AssetRow>
        id="connector-assets"
        label={t('cockpit.assets.title')}
        columns={columns}
        rows={rows}
        state={state}
        onStateChange={(patch) => setState((s) => ({ ...s, ...patch }))}
        activeFilter={false}
        searchFields={['id', 'typeLabel']}
        searchPlaceholder={t('cockpit.assets.search')}
        isLoading={query.isPending}
        isFetching={query.isFetching}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.id}
        rowActions={(row) => [{ label: t('cockpit.assets.details'), icon: Eye, onSelect: () => onSelect(row.id) }]}
        emptyState={{
          icon: Package,
          title: t('cockpit.assets.empty'),
          description: t('cockpit.assets.emptyDescription'),
        }}
      />
      <JsonSheet
        open={Boolean(asset)}
        onClose={() => onSelect(undefined)}
        title={asset?.id ?? ''}
        description={asset?.type ?? undefined}
        badges={asset ? <ManagedBadge managed={asset.managed} /> : null}
        sections={[
          { title: t('cockpit.assets.properties'), value: asset?.properties },
          ...(asset?.offers ?? []).flatMap((o) => [
            {
              title: t('cockpit.assets.usagePolicy', { id: o.definition.contractPolicyId }),
              value: o.contractPolicy?.policy,
              summary: o.contractPolicy ? <ConstraintChips policy={o.contractPolicy.policy} /> : undefined,
            },
            ...(o.definition.accessPolicyId !== o.definition.contractPolicyId
              ? [
                  {
                    title: t('cockpit.assets.accessPolicy', { id: o.definition.accessPolicyId }),
                    value: o.accessPolicy?.policy,
                    summary: o.accessPolicy ? <ConstraintChips policy={o.accessPolicy.policy} /> : undefined,
                  },
                ]
              : []),
          ]),
        ]}
      />
    </section>
  )
}
