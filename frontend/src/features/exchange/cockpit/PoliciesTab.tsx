import type { ColumnDef } from '@tanstack/react-table'
import { Eye, FileSignature, ShieldCheck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable, DEFAULT_PAGE_SIZE, type ListState } from '@/components/data-table'

import { useConnectorPolicies, useContractDefinitions } from '../api/connector-hooks'
import type { ConnectorContractDefinition, ConnectorPolicy } from '../api/connector'
import { GlossaryTerm } from '../components/parts'
import { ConstraintChips, ManagedBadge } from './cockpit-parts'
import { JsonSheet } from './JsonSheet'

type Selection = { kind: 'policy'; id: string } | { kind: 'definition'; id: string } | null

/** Cockpit › Policies & Verträge: policy definitions (ODRL) and contract definitions of the own EDC. */
export function PoliciesTab() {
  const { t } = useTranslation('exchange')
  const policies = useConnectorPolicies()
  const definitions = useContractDefinitions()
  const [policyState, setPolicyState] = useState<ListState>({ page: 0, size: DEFAULT_PAGE_SIZE, sort: 'id,asc' })
  const [defState, setDefState] = useState<ListState>({ page: 0, size: DEFAULT_PAGE_SIZE, sort: 'id,asc' })
  const [selected, setSelected] = useState<Selection>(null)

  const policyColumns = useMemo<ColumnDef<ConnectorPolicy, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: t('cockpit.policies.columns.id'),
        meta: { label: t('cockpit.policies.columns.id'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            onClick={() => setSelected({ kind: 'policy', id: row.original.id })}
            className="rounded-sm text-left font-mono text-xs font-medium break-all underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {row.original.id}
          </button>
        ),
      },
      {
        id: 'constraints',
        header: t('cockpit.policies.columns.constraints'),
        meta: { label: t('cockpit.policies.columns.constraints'), sortField: false },
        cell: ({ row }) => <ConstraintChips policy={row.original.policy} />,
      },
      {
        id: 'managed',
        header: t('cockpit.assets.columns.managed'),
        meta: { label: t('cockpit.assets.columns.managed'), sortField: false },
        cell: ({ row }) => <ManagedBadge managed={row.original.managed} />,
      },
    ],
    [t],
  )

  const definitionColumns = useMemo<ColumnDef<ConnectorContractDefinition, unknown>[]>(
    () => [
      {
        id: 'id',
        accessorKey: 'id',
        header: t('cockpit.definitions.columns.id'),
        meta: { label: t('cockpit.definitions.columns.id'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            onClick={() => setSelected({ kind: 'definition', id: row.original.id })}
            className="rounded-sm text-left font-mono text-xs font-medium break-all underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {row.original.id}
          </button>
        ),
      },
      {
        id: 'accessPolicyId',
        accessorKey: 'accessPolicyId',
        header: t('cockpit.definitions.columns.accessPolicy'),
        meta: { label: t('cockpit.definitions.columns.accessPolicy') },
        cell: ({ row }) => <span className="font-mono text-[11px] break-all">{row.original.accessPolicyId}</span>,
      },
      {
        id: 'contractPolicyId',
        accessorKey: 'contractPolicyId',
        header: t('cockpit.definitions.columns.contractPolicy'),
        meta: { label: t('cockpit.definitions.columns.contractPolicy') },
        cell: ({ row }) => <span className="font-mono text-[11px] break-all">{row.original.contractPolicyId}</span>,
      },
      {
        id: 'managed',
        header: t('cockpit.assets.columns.managed'),
        meta: { label: t('cockpit.assets.columns.managed'), sortField: false },
        cell: ({ row }) => <ManagedBadge managed={row.original.managed} />,
      },
    ],
    [t],
  )

  const policy = selected?.kind === 'policy' ? policies.data?.find((p) => p.id === selected.id) : undefined
  const definition = selected?.kind === 'definition' ? definitions.data?.find((d) => d.id === selected.id) : undefined

  return (
    <div className="space-y-8">
      <p className="max-w-3xl text-xs text-muted-foreground">{t('cockpit.policies.intro')}</p>
      <section className="space-y-3" aria-labelledby="cockpit-policies">
        <h2 id="cockpit-policies" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          {t('cockpit.policies.title')}
          <GlossaryTerm term="POLICY" />
          <GlossaryTerm term="ODRL" />
        </h2>
        <DataTable<ConnectorPolicy>
          id="connector-policies"
          label={t('cockpit.policies.title')}
          columns={policyColumns}
          rows={policies.data}
          state={policyState}
          onStateChange={(patch) => setPolicyState((s) => ({ ...s, ...patch }))}
          activeFilter={false}
          searchFields={['id']}
          searchPlaceholder={t('cockpit.policies.search')}
          isLoading={policies.isPending}
          error={policies.error}
          onRetry={() => void policies.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.id}
          rowActions={(row) => [
            {
              label: t('cockpit.policies.viewJson'),
              icon: Eye,
              onSelect: () => setSelected({ kind: 'policy', id: row.id }),
            },
          ]}
          emptyState={{ icon: ShieldCheck, title: t('cockpit.policies.empty') }}
        />
      </section>
      <section className="space-y-3" aria-labelledby="cockpit-definitions">
        <h2 id="cockpit-definitions" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          {t('cockpit.definitions.title')}
          <GlossaryTerm term="CONTRACT_DEFINITION" />
        </h2>
        <DataTable<ConnectorContractDefinition>
          id="connector-contract-definitions"
          label={t('cockpit.definitions.title')}
          columns={definitionColumns}
          rows={definitions.data}
          state={defState}
          onStateChange={(patch) => setDefState((s) => ({ ...s, ...patch }))}
          activeFilter={false}
          searchFields={['id', 'accessPolicyId', 'contractPolicyId']}
          searchPlaceholder={t('cockpit.definitions.search')}
          isLoading={definitions.isPending}
          error={definitions.error}
          onRetry={() => void definitions.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.id}
          rowActions={(row) => [
            {
              label: t('cockpit.policies.viewJson'),
              icon: Eye,
              onSelect: () => setSelected({ kind: 'definition', id: row.id }),
            },
          ]}
          emptyState={{ icon: FileSignature, title: t('cockpit.definitions.empty') }}
        />
      </section>
      <JsonSheet
        open={Boolean(policy)}
        onClose={() => setSelected(null)}
        title={policy?.id ?? ''}
        badges={policy ? <ManagedBadge managed={policy.managed} /> : null}
        sections={[
          {
            title: t('cockpit.policies.odrl'),
            value: policy?.policy,
            summary: policy ? <ConstraintChips policy={policy.policy} /> : undefined,
          },
        ]}
      />
      <JsonSheet
        open={Boolean(definition)}
        onClose={() => setSelected(null)}
        title={definition?.id ?? ''}
        badges={definition ? <ManagedBadge managed={definition.managed} /> : null}
        sections={[
          {
            title: t('cockpit.definitions.policies'),
            value: definition
              ? { accessPolicyId: definition.accessPolicyId, contractPolicyId: definition.contractPolicyId }
              : undefined,
          },
          { title: t('cockpit.definitions.selector'), value: definition?.assetsSelector },
        ]}
      />
    </div>
  )
}
