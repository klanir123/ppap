import type { ColumnDef } from '@tanstack/react-table'
import { Link, useNavigate } from '@tanstack/react-router'
import { Eye, Plus, ShieldCheck } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable, DEFAULT_PAGE_SIZE, type ListState } from '@/components/data-table'
import { FormSheet } from '@/components/forms'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { GlossaryTerm } from '@/features/exchange/components/parts'

import { policyProfilesApi, type PolicyProfile } from '../api'
import { PolicyProfileFields } from './PolicyProfileFields'
import { policyProfileDefaults, policyProfileSchema, toPolicyProfileInput } from './policy-profile-schema'
import { toastReconciliation } from './reconciliation'

/** Policy profiles (UC-10 / E5-04): access + usage rules per partner relation, applied to the own EDC. */
export function PolicyProfilesPage() {
  const { t } = useTranslation(['masterdata', 'forms'])
  const navigate = useNavigate()
  const query = policyProfilesApi.useAll({})
  const save = policyProfilesApi.useSave()
  const [creating, setCreating] = useState(false)
  const [state, setState] = useState<ListState>({ page: 0, size: DEFAULT_PAGE_SIZE, sort: 'code,asc', active: 'all' })

  const open = (row: PolicyProfile) =>
    void navigate({ to: '/policy-profiles/$profileId', params: { profileId: row.id } })

  const columns = useMemo<ColumnDef<PolicyProfile, unknown>[]>(
    () => [
      {
        id: 'code',
        accessorKey: 'code',
        header: t('policyProfiles.columns.code'),
        meta: { label: t('policyProfiles.columns.code'), hideable: false },
        cell: ({ row }) => (
          <Link
            to="/policy-profiles/$profileId"
            params={{ profileId: row.original.id }}
            className="rounded-sm font-mono text-xs font-semibold underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {row.original.code}
          </Link>
        ),
      },
      {
        id: 'name',
        accessorKey: 'name',
        header: t('policyProfiles.columns.name'),
        meta: { label: t('policyProfiles.columns.name') },
        cell: ({ row }) => (
          <span className="flex min-w-0 flex-col">
            <span className="truncate font-medium">{row.original.name}</span>
            {row.original.description ? (
              <span className="line-clamp-1 text-xs text-muted-foreground">{row.original.description}</span>
            ) : null}
          </span>
        ),
      },
      {
        id: 'access',
        header: t('policyProfiles.columns.access'),
        meta: { label: t('policyProfiles.columns.access'), sortField: false },
        cell: ({ row }) => (
          <span className="text-xs">
            {t(`policyProfiles.access.${row.original.access.type}`)}
            {row.original.access.membershipRequired ? (
              <span className="block text-muted-foreground">{t('policyProfiles.columns.membership')}</span>
            ) : null}
          </span>
        ),
      },
      {
        id: 'usage',
        header: t('policyProfiles.columns.usage'),
        meta: { label: t('policyProfiles.columns.usage'), sortField: false },
        cell: ({ row }) => (
          <span className="flex flex-col gap-1">
            <span className="font-mono text-[11px]">{row.original.usage.frameworkAgreement}</span>
            <span className="flex flex-wrap gap-1">
              {row.original.usage.usagePurposes.map((p) => (
                <span key={p} className="rounded-full border bg-muted/50 px-1.5 font-mono text-[10.5px]">
                  {p}
                </span>
              ))}
            </span>
          </span>
        ),
      },
      {
        id: 'active',
        header: t('policyProfiles.columns.active'),
        meta: { label: t('policyProfiles.columns.active'), sortField: false },
        cell: ({ row }) => <StatusBadge kind="active" status={row.original.active} size="sm" />,
      },
      {
        id: 'version',
        accessorKey: 'version',
        header: t('policyProfiles.columns.version'),
        meta: {
          label: t('policyProfiles.columns.version'),
          className: 'text-right tabular',
          headerClassName: 'text-right',
        },
      },
    ],
    [t],
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('policyProfiles.title')}
        description={t('policyProfiles.description')}
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden="true" />
            {t('policyProfiles.new')}
          </Button>
        }
      />
      <p className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        {t('policyProfiles.glossary')}
        <GlossaryTerm term="POLICY" />
        <GlossaryTerm term="ODRL" />
        <GlossaryTerm term="CONTRACT_DEFINITION" />
      </p>
      <DataTable<PolicyProfile>
        id="policy-profiles"
        label={t('policyProfiles.title')}
        columns={columns}
        rows={query.data}
        state={state}
        onStateChange={(patch) => setState((s) => ({ ...s, ...patch }))}
        searchFields={['code', 'name', 'description']}
        isLoading={query.isPending}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.code}
        rowActions={(row) => [{ label: t('policyProfiles.open'), icon: Eye, onSelect: () => open(row) }]}
        emptyState={{
          icon: ShieldCheck,
          title: t('policyProfiles.empty'),
          description: t('policyProfiles.emptyDescription'),
        }}
        hidePagination
      />
      <FormSheet
        open={creating}
        onOpenChange={setCreating}
        title={t('policyProfiles.new')}
        description={t('policyProfiles.newDescription')}
        schema={policyProfileSchema}
        defaultValues={policyProfileDefaults()}
        formKey="new"
        submitLabel={t('forms:create')}
        onSubmit={async (values) => {
          const created = await save.mutateAsync({ input: toPolicyProfileInput(values) })
          toastReconciliation(created.reconciliation, t('policyProfiles.created', { name: created.name }))
          void navigate({ to: '/policy-profiles/$profileId', params: { profileId: created.id } })
        }}
      >
        {() => <PolicyProfileFields isNew />}
      </FormSheet>
    </div>
  )
}
