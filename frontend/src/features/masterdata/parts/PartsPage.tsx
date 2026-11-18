import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { Package, Pencil, Plus, Power, Upload, RotateCcw } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, toQueryParams } from '@/components/data-table'
import { FormSheet, TextField } from '@/components/forms'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { usePermission } from '@/features/auth/current-user'

import { partsApi, invalidateResource } from '../api'
import { partRequest } from '../api/requests'
import type { Part } from '../api/types'
import { importApi } from '../import/api'
import { ImportWizard } from '../import/ImportWizard'
import { AuditTrail } from '../shared/AuditTrail'
import { ActiveBadge, MonoText, Muted } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { localizedName, useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { partDefaults, partSchema, toPartInput } from './part-schema'

const routeApi = getRouteApi('/masterdata/parts')
const partsImport = importApi('parts')

export function PartsPage() {
  const { t, i18n } = useTranslation(['masterdata', 'forms'])
  const canWrite = usePermission('masterdata:write')
  const queryClient = useQueryClient()
  const navigate = routeApi.useNavigate()
  const { state, onStateChange } = useListState(
    routeApi.useSearch(),
    (search) => void navigate({ search: (prev) => ({ ...prev, ...search }), replace: true }),
  )
  const query = partsApi.usePage(toQueryParams(state))
  const save = partsApi.useSave()
  const deactivate = partsApi.useDeactivate()
  const reactivate = partsApi.useReactivate(partRequest)
  const crud = useCrudState<Part>()
  const editing = crud.current
  const { openEdit } = crud
  const invalidate = () => void invalidateResource(queryClient, partsApi.keyPrefix)

  const columns = useMemo<ColumnDef<Part, unknown>[]>(
    () => [
      {
        id: 'partNumber',
        accessorKey: 'partNumber',
        header: t('parts.partNumber'),
        meta: { label: t('parts.partNumber'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            className="rounded-sm text-left font-mono text-xs font-medium whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            onClick={() => openEdit(row.original)}
          >
            {row.original.partNumber}
          </button>
        ),
      },
      {
        id: i18n.resolvedLanguage === 'en' ? 'nameEn' : 'nameDe',
        header: t('parts.name'),
        meta: { label: t('parts.name'), hideable: false },
        cell: ({ row }) => (
          <span className="block max-w-80 truncate">{localizedName(row.original, i18n.resolvedLanguage)}</span>
        ),
      },
      {
        id: 'drawingNumber',
        header: t('parts.drawing'),
        meta: { label: t('parts.drawing') },
        cell: ({ row }) =>
          row.original.drawingNumber ? (
            <MonoText>
              {row.original.drawingNumber}
              {row.original.drawingRevision ? <Muted> · {row.original.drawingRevision}</Muted> : null}
            </MonoText>
          ) : (
            <Muted>–</Muted>
          ),
      },
      { id: 'material', accessorKey: 'material', header: t('parts.material'), meta: { label: t('parts.material') } },
      {
        id: 'catenaXId',
        header: t('parts.catenaXId'),
        meta: { label: t('parts.catenaXId'), sortField: false },
        cell: ({ row }) =>
          row.original.catenaXId ? (
            <MonoText className="text-muted-foreground">{row.original.catenaXId}</MonoText>
          ) : (
            <Muted>–</Muted>
          ),
      },
      {
        id: 'active',
        header: t('common.status'),
        meta: { label: t('common.status') },
        cell: ({ row }) => <ActiveBadge active={row.original.active} />,
      },
    ],
    [t, i18n.resolvedLanguage, openEdit],
  )

  return (
    <div className="space-y-6">
      <PageHeader title={t('parts.title')} description={t('parts.description')} />
      {canWrite ? null : <ReadOnlyHint />}
      <DataTable<Part>
        id="parts"
        label={t('parts.title')}
        columns={columns}
        page={query.data}
        state={state}
        onStateChange={onStateChange}
        isLoading={query.isPending}
        isFetching={query.isFetching}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.partNumber}
        searchPlaceholder={t('parts.search')}
        rowActions={(row) => [
          { label: canWrite ? t('common.edit') : t('common.view'), icon: Pencil, onSelect: () => crud.openEdit(row) },
          {
            label: t('common.deactivate'),
            icon: Power,
            destructive: true,
            hidden: !canWrite || !row.active,
            onSelect: () => crud.setDeactivating(row),
          },
          {
            label: t('common.reactivate'),
            icon: RotateCcw,
            hidden: !canWrite || row.active,
            onSelect: () =>
              reactivate.mutate(row, {
                onSuccess: () => toast.success(t('common.reactivated', { name: row.partNumber })),
              }),
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
                {t('parts.new')}
              </Button>
            </>
          ) : null
        }
        emptyState={{
          icon: Package,
          title: t('parts.empty'),
          description: t('parts.emptyDescription'),
          action: canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('parts.new')}
            </Button>
          ) : undefined,
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('parts.new') : t('parts.editTitle')}
        description={crud.isNew ? t('parts.newDescription') : editing?.partNumber}
        schema={partSchema}
        defaultValues={partDefaults(editing)}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void partsApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          const saved = await save.mutateAsync({
            id: editing?.id,
            version: editing?.version,
            input: toPartInput(values),
          })
          toast.success(
            t(crud.isNew ? 'common.created' : 'common.saved', { name: saved?.partNumber ?? values.partNumber }),
          )
        }}
      >
        {() => (
          <>
            <TextField name="partNumber" label={t('parts.partNumber')} required mono disabled={!crud.isNew} />
            <div className="grid gap-5 sm:grid-cols-2">
              <TextField name="nameDe" label={t('parts.nameDe')} required />
              <TextField name="nameEn" label={t('parts.nameEn')} />
            </div>
            <div className="grid gap-5 sm:grid-cols-[2fr_1fr]">
              <TextField name="drawingNumber" label={t('parts.drawingNumber')} mono />
              <TextField name="drawingRevision" label={t('parts.drawingRevision')} mono />
            </div>
            <TextField name="material" label={t('parts.material')} />
            <TextField
              name="catenaXId"
              label={t('parts.catenaXId')}
              description={t('parts.catenaXIdHint')}
              mono
              placeholder="urn:uuid:…"
            />
            {editing ? (
              <>
                <Separator />
                <AuditTrail objectType="PART" objectId={editing.id} />
              </>
            ) : null}
          </>
        )}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating?.partNumber}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />

      <ImportWizard
        open={crud.importOpen}
        onOpenChange={crud.setImportOpen}
        entity="parts"
        entityLabel={t('parts.title')}
        {...partsImport}
        onCompleted={invalidate}
      />
    </div>
  )
}
