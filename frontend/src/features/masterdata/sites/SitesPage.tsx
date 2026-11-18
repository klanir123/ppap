import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { MapPin, Pencil, Plus, Power, Upload } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, toQueryParams } from '@/components/data-table'
import { FormSheet, SelectField, TextField } from '@/components/forms'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { usePermission } from '@/features/auth/current-user'
import { useAppConfig } from '@/features/config/config-context'

import { sitesApi, invalidateResource } from '../api'
import type { Site } from '../api/types'
import { importApi } from '../import/api'
import { ImportWizard } from '../import/ImportWizard'
import { AuditTrail } from '../shared/AuditTrail'
import { ActiveBadge, MonoText, Muted } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { usePartnerOptions } from '../shared/usePartnerOptions'
import { siteDefaults, siteSchema, toSiteInput } from './site-schema'

const routeApi = getRouteApi('/masterdata/sites')
const sitesImport = importApi('sites')

export function SitesPage() {
  const { t } = useTranslation(['masterdata', 'forms'])
  const canWrite = usePermission('masterdata:write')
  const { participant } = useAppConfig()
  const queryClient = useQueryClient()
  const navigate = routeApi.useNavigate()
  const { state, onStateChange } = useListState(
    routeApi.useSearch(),
    (search) => void navigate({ search: (prev) => ({ ...prev, ...search }), replace: true }),
  )
  const query = sitesApi.usePage(toQueryParams(state))
  const save = sitesApi.useSave()
  const deactivate = sitesApi.useDeactivate()
  const crud = useCrudState<Site>()
  const editing = crud.current
  const { openEdit } = crud
  const owners = usePartnerOptions({ includeOwn: true })

  const columns = useMemo<ColumnDef<Site, unknown>[]>(
    () => [
      {
        id: 'bpns',
        header: t('sites.bpns'),
        meta: { label: t('sites.bpns'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            className="rounded-sm font-mono text-xs font-medium whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            onClick={() => openEdit(row.original)}
          >
            {row.original.bpns}
          </button>
        ),
      },
      { id: 'name', accessorKey: 'name', header: t('sites.name'), meta: { label: t('sites.name'), hideable: false } },
      {
        id: 'ownerName',
        header: t('sites.owner'),
        meta: { label: t('sites.owner') },
        cell: ({ row }) => (
          <span className="flex flex-col">
            <span>
              {row.original.ownerBpnl === participant.bpnl ? t('sites.ownTenant') : (row.original.ownerName ?? '–')}
            </span>
            <MonoText className="text-muted-foreground">{row.original.ownerBpnl}</MonoText>
          </span>
        ),
      },
      {
        id: 'city',
        header: t('sites.address'),
        meta: { label: t('sites.address') },
        cell: ({ row }) => {
          const { street, postalCode, city } = row.original
          const line = [street, [postalCode, city].filter(Boolean).join(' ')].filter(Boolean).join(', ')
          return line ? <span className="block max-w-72 truncate">{line}</span> : <Muted>–</Muted>
        },
      },
      { id: 'country', accessorKey: 'country', header: t('sites.country'), meta: { label: t('sites.country') } },
      {
        id: 'active',
        header: t('common.status'),
        meta: { label: t('common.status') },
        cell: ({ row }) => <ActiveBadge active={row.original.active} />,
      },
    ],
    [t, openEdit, participant.bpnl],
  )

  return (
    <div className="space-y-6">
      <PageHeader title={t('sites.title')} description={t('sites.description')} />
      {canWrite ? null : <ReadOnlyHint />}
      <DataTable<Site>
        id="sites"
        label={t('sites.title')}
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
        searchPlaceholder={t('sites.search')}
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
                {t('sites.new')}
              </Button>
            </>
          ) : null
        }
        emptyState={{
          icon: MapPin,
          title: t('sites.empty'),
          description: t('sites.emptyDescription'),
          action: canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('sites.new')}
            </Button>
          ) : undefined,
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('sites.new') : t('sites.editTitle')}
        description={crud.isNew ? t('sites.newDescription') : editing?.name}
        schema={siteSchema}
        defaultValues={siteDefaults(editing, participant.bpnl)}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void sitesApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({ id: editing?.id, version: editing?.version, input: toSiteInput(values) })
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.name }))
        }}
      >
        {() => (
          <>
            <TextField
              name="bpns"
              label={t('sites.bpns')}
              required
              mono
              uppercase
              disabled={!crud.isNew}
              placeholder="BPNS…"
            />
            <TextField name="name" label={t('sites.name')} required />
            <SelectField name="ownerBpnl" label={t('sites.ownerBpnl')} required options={owners.options} />
            <TextField name="street" label={t('sites.street')} />
            <div className="grid gap-5 sm:grid-cols-[1fr_2fr_1fr]">
              <TextField name="postalCode" label={t('sites.postalCode')} />
              <TextField name="city" label={t('sites.city')} />
              <TextField
                name="country"
                label={t('sites.country')}
                description={t('sites.countryHint')}
                uppercase
                mono
              />
            </div>
            {editing ? (
              <>
                <Separator />
                <AuditTrail objectType="SITE" objectId={editing.id} />
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
        entity="sites"
        entityLabel={t('sites.title')}
        {...sitesImport}
        onCompleted={() => void invalidateResource(queryClient, sitesApi.keyPrefix)}
      />
    </div>
  )
}
