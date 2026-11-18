import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { Pencil, Plus, Power, Upload, Users } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, toQueryParams } from '@/components/data-table'
import { CheckboxGroupField, FormSheet, TextField } from '@/components/forms'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { usePermission } from '@/features/auth/current-user'
import { useAppConfig } from '@/features/config/config-context'

import { partnersApi, invalidateResource } from '../api'
import { PARTNER_ROLES, type Partner } from '../api/types'
import { importApi } from '../import/api'
import { ImportWizard } from '../import/ImportWizard'
import { AuditTrail } from '../shared/AuditTrail'
import { ActiveBadge, ExchangeReadyIndicator, MonoText, Muted, RoleChips } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { createPartnerSchema, partnerDefaults, toPartnerInput } from './partner-schema'

const routeApi = getRouteApi('/masterdata/partners')
const partnersImport = importApi('partners')

export function PartnersPage() {
  const { t } = useTranslation(['masterdata', 'forms'])
  const canWrite = usePermission('partners:write')
  const queryClient = useQueryClient()
  const navigate = routeApi.useNavigate()
  const { state, onStateChange } = useListState(
    routeApi.useSearch(),
    (search) => void navigate({ search: (prev) => ({ ...prev, ...search }), replace: true }),
  )
  const query = partnersApi.usePage(toQueryParams(state))
  const save = partnersApi.useSave()
  const deactivate = partnersApi.useDeactivate()
  const crud = useCrudState<Partner>()
  const editing = crud.current
  const { openEdit } = crud
  const { participant } = useAppConfig()
  const partnerSchema = useMemo(() => createPartnerSchema(participant.bpnl), [participant.bpnl])

  const columns = useMemo<ColumnDef<Partner, unknown>[]>(
    () => [
      {
        id: 'bpnl',
        header: t('partners.bpnl'),
        meta: { label: t('partners.bpnl'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            className="rounded-sm font-mono text-xs font-medium whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            onClick={() => openEdit(row.original)}
          >
            {row.original.bpnl}
          </button>
        ),
      },
      {
        id: 'name',
        accessorKey: 'name',
        header: t('partners.name'),
        meta: { label: t('partners.name'), hideable: false },
      },
      {
        id: 'roles',
        header: t('partners.roles'),
        meta: { label: t('partners.roles'), sortField: false },
        cell: ({ row }) => <RoleChips roles={row.original.roles} />,
      },
      {
        id: 'exchangeReady',
        header: t('partners.exchange'),
        meta: { label: t('partners.exchange'), sortField: false },
        cell: ({ row }) => <ExchangeReadyIndicator ready={row.original.exchangeReady} />,
      },
      {
        id: 'dspEndpoint',
        header: t('partners.dspEndpoint'),
        meta: { label: t('partners.dspEndpoint'), sortField: false },
        cell: ({ row }) =>
          row.original.dspEndpoint ? (
            <MonoText className="block max-w-64 truncate text-muted-foreground">{row.original.dspEndpoint}</MonoText>
          ) : (
            <Muted>–</Muted>
          ),
      },
      {
        id: 'contactEmail',
        accessorKey: 'contactEmail',
        header: t('partners.contactEmail'),
        meta: { label: t('partners.contactEmail') },
      },
      {
        id: 'active',
        header: t('common.status'),
        meta: { label: t('common.status') },
        cell: ({ row }) => <ActiveBadge active={row.original.active} />,
      },
    ],
    [t, openEdit],
  )

  return (
    <div className="space-y-6">
      <PageHeader title={t('partners.title')} description={t('partners.description')} />
      {canWrite ? null : <ReadOnlyHint />}
      <DataTable<Partner>
        id="partners"
        label={t('partners.title')}
        columns={columns}
        page={query.data}
        state={state}
        onStateChange={onStateChange}
        isLoading={query.isPending}
        isFetching={query.isFetching}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.name}
        searchPlaceholder={t('partners.search')}
        rowActions={(row) => [
          { label: canWrite ? t('common.edit') : t('common.view'), icon: Pencil, onSelect: () => crud.openEdit(row) },
          {
            label: t('common.deactivate'),
            icon: Power,
            destructive: true,
            hidden: !canWrite || !row.active,
            onSelect: () => crud.setDeactivating(row),
          },
        ]}
        toolbar={
          canWrite ? (
            <>
              <Button variant="outline" size="sm" onClick={() => crud.setImportOpen(true)}>
                <Upload aria-hidden="true" />
                {t('common.import')}
              </Button>
              <Button size="sm" onClick={crud.openNew}>
                <Plus aria-hidden="true" />
                {t('partners.new')}
              </Button>
            </>
          ) : null
        }
        emptyState={{
          icon: Users,
          title: t('partners.empty'),
          description: t('partners.emptyDescription'),
          action: canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('partners.new')}
            </Button>
          ) : undefined,
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('partners.new') : t('partners.editTitle')}
        description={crud.isNew ? t('partners.newDescription') : editing?.name}
        schema={partnerSchema}
        defaultValues={partnerDefaults(editing)}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void partnersApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({ id: editing?.id, version: editing?.version, input: toPartnerInput(values) })
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.name }))
        }}
      >
        {() => (
          <>
            {editing ? <ExchangeReadyIndicator ready={editing.exchangeReady} /> : null}
            <TextField
              name="bpnl"
              label={t('partners.bpnl')}
              required
              mono
              uppercase
              disabled={!crud.isNew}
              placeholder="BPNL…"
            />
            <TextField name="name" label={t('partners.name')} required />
            <CheckboxGroupField
              name="roles"
              label={t('partners.roles')}
              description={t('partners.rolesHint')}
              required
              options={PARTNER_ROLES.map((role) => ({ value: role, label: t(`partners.roleValues.${role}`) }))}
            />
            <TextField name="contactEmail" label={t('partners.contactEmail')} type="email" />
            <Separator />
            <h3 className="text-sm font-semibold">{t('partners.connectorSection')}</h3>
            <TextField
              name="dspEndpoint"
              label={t('partners.dspEndpoint')}
              description={t('partners.dspEndpointHint')}
              type="url"
              mono
            />
            <TextField name="did" label={t('partners.did')} mono placeholder="did:web:…" />
            {editing ? (
              <>
                <Separator />
                <AuditTrail objectType="PARTNER" objectId={editing.id} />
              </>
            ) : null}
          </>
        )}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating?.name}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />

      <ImportWizard
        open={crud.importOpen}
        onOpenChange={crud.setImportOpen}
        entity="partners"
        entityLabel={t('partners.title')}
        {...partnersImport}
        onCompleted={() => void invalidateResource(queryClient, partnersApi.keyPrefix)}
      />
    </div>
  )
}
