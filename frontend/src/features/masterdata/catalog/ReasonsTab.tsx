import type { ColumnDef } from '@tanstack/react-table'
import { ListChecks, Pencil, Plus, Power, RotateCcw } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, type ListState } from '@/components/data-table'
import { FormSheet } from '@/components/forms'
import { Button } from '@/components/ui/button'

import { reasonsApi } from '../api'
import { reasonRequest } from '../api/requests'
import type { CatalogReason } from '../api/types'
import { ActiveBadge, Muted } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { localizedName } from '../shared/list-state'
import { nextSortOrder, reasonDefaults, reasonSchema, toReasonInput } from './catalog-schema'
import { CatalogEntryFields } from './CatalogEntryFields'

interface TabProps {
  canWrite: boolean
  state: ListState
  onStateChange: (patch: Partial<ListState>) => void
}

export function ReasonsTab({ canWrite, state, onStateChange }: TabProps) {
  const { t, i18n } = useTranslation(['masterdata', 'forms'])
  const query = reasonsApi.useAll({})
  const save = reasonsApi.useSave()
  const deactivate = reasonsApi.useDeactivate()
  const reactivate = reasonsApi.useReactivate(reasonRequest)
  const crud = useCrudState<CatalogReason>()
  const editing = crud.current
  const { openEdit } = crud
  const lng = i18n.resolvedLanguage

  const columns = useMemo<ColumnDef<CatalogReason, unknown>[]>(
    () => [
      {
        id: 'sortOrder',
        header: '#',
        meta: { label: t('catalog.sortOrder'), className: 'w-12 tabular text-muted-foreground' },
        cell: ({ row }) => row.original.sortOrder,
      },
      {
        id: 'code',
        header: t('catalog.code'),
        meta: { label: t('catalog.code'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            className="rounded-sm font-mono text-xs font-medium whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            onClick={() => openEdit(row.original)}
          >
            {row.original.code}
          </button>
        ),
      },
      {
        id: lng === 'en' ? 'nameEn' : 'nameDe',
        header: t('catalog.name'),
        meta: { label: t('catalog.name'), hideable: false },
        cell: ({ row }) => localizedName(row.original, lng),
      },
      {
        id: lng === 'en' ? 'descriptionEn' : 'descriptionDe',
        header: t('catalog.descriptionLabel'),
        meta: { label: t('catalog.descriptionLabel'), sortField: false },
        cell: ({ row }) => {
          const text = lng === 'en' ? row.original.descriptionEn : row.original.descriptionDe
          return text ? <span className="block max-w-md truncate text-muted-foreground">{text}</span> : <Muted>–</Muted>
        },
      },
      {
        id: 'active',
        header: t('common.status'),
        meta: { label: t('common.status') },
        cell: ({ row }) => <ActiveBadge active={row.original.active} />,
      },
    ],
    [t, lng, openEdit],
  )

  return (
    <>
      <DataTable<CatalogReason>
        id="catalog-reasons"
        label={t('catalog.tabs.reasons')}
        columns={columns}
        rows={query.data}
        state={state}
        onStateChange={onStateChange}
        isLoading={query.isPending}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.code}
        searchFields={['code', 'nameDe', 'nameEn']}
        searchPlaceholder={t('catalog.searchReasons')}
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
              reactivate.mutate(row, { onSuccess: () => toast.success(t('common.reactivated', { name: row.code })) }),
          },
        ]}
        toolbar={
          canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('catalog.reasons.new')}
            </Button>
          ) : null
        }
        emptyState={{
          icon: ListChecks,
          title: t('catalog.reasons.empty'),
          description: t('catalog.reasons.emptyDescription'),
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('catalog.reasons.new') : t('catalog.reasons.editTitle')}
        description={editing?.code}
        schema={reasonSchema}
        defaultValues={reasonDefaults(editing, nextSortOrder(query.data))}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void reasonsApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({ id: editing?.id, version: editing?.version, input: toReasonInput(values) })
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.code }))
        }}
      >
        {() => <CatalogEntryFields isNew={crud.isNew} />}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating?.code}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />
    </>
  )
}
