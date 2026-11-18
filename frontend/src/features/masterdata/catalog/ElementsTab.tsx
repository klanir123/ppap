import type { ColumnDef } from '@tanstack/react-table'
import { ListChecks, Pencil, Plus, Power, RotateCcw } from 'lucide-react'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, type ListState } from '@/components/data-table'
import { FormSheet, JsonField, SelectField, TextField } from '@/components/forms'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

import { elementsApi } from '../api'
import { elementRequest } from '../api/requests'
import { EVIDENCE_TYPES, type CatalogElement } from '../api/types'
import { ActiveBadge, MonoText, Muted } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { localizedName } from '../shared/list-state'
import { elementDefaults, elementSchema, nextSortOrder, toElementInput } from './catalog-schema'
import { CatalogEntryFields } from './CatalogEntryFields'

interface TabProps {
  canWrite: boolean
  state: ListState
  onStateChange: (patch: Partial<ListState>) => void
}

export function ElementsTab({ canWrite, state, onStateChange }: TabProps) {
  const { t, i18n } = useTranslation(['masterdata', 'forms'])
  const query = elementsApi.useAll({})
  const save = elementsApi.useSave()
  const deactivate = elementsApi.useDeactivate()
  const reactivate = elementsApi.useReactivate(elementRequest)
  const crud = useCrudState<CatalogElement>()
  const editing = crud.current
  const { openEdit } = crud
  const lng = i18n.resolvedLanguage

  const columns = useMemo<ColumnDef<CatalogElement, unknown>[]>(
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
        cell: ({ row }) => <span className="block max-w-96 truncate">{localizedName(row.original, lng)}</span>,
      },
      {
        id: 'evidenceType',
        header: t('catalog.elements.evidenceType'),
        meta: { label: t('catalog.elements.evidenceType') },
        cell: ({ row }) => (
          <span className="whitespace-nowrap">
            {t(`catalog.elements.evidenceTypeValues.${row.original.evidenceType}`)}
          </span>
        ),
      },
      {
        id: 'referenceVda',
        header: t('catalog.elements.references'),
        meta: { label: t('catalog.elements.references'), sortField: false },
        cell: ({ row }) => {
          const refs = [row.original.referenceVda, row.original.referenceAiag].filter(Boolean)
          return refs.length ? (
            <span className="text-xs text-muted-foreground">{refs.join(' · ')}</span>
          ) : (
            <Muted>–</Muted>
          )
        },
      },
      {
        id: 'valueSchema',
        header: t('catalog.elements.valueSchema'),
        meta: { label: t('catalog.elements.valueSchema'), sortField: false },
        cell: ({ row }) => (row.original.valueSchema ? <MonoText>JSON</MonoText> : <Muted>–</Muted>),
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
      <DataTable<CatalogElement>
        id="catalog-elements"
        label={t('catalog.tabs.elements')}
        columns={columns}
        rows={query.data}
        state={state}
        onStateChange={onStateChange}
        isLoading={query.isPending}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.code}
        searchFields={['code', 'nameDe', 'nameEn', 'referenceVda', 'referenceAiag']}
        searchPlaceholder={t('catalog.searchElements')}
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
              {t('catalog.elements.new')}
            </Button>
          ) : null
        }
        emptyState={{
          icon: ListChecks,
          title: t('catalog.elements.empty'),
          description: t('catalog.elements.emptyDescription'),
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('catalog.elements.new') : t('catalog.elements.editTitle')}
        description={editing?.code}
        schema={elementSchema}
        defaultValues={elementDefaults(editing, nextSortOrder(query.data))}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void elementsApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({ id: editing?.id, version: editing?.version, input: toElementInput(values) })
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.code }))
        }}
      >
        {() => (
          <>
            <CatalogEntryFields isNew={crud.isNew} />
            <Separator />
            <div className="grid gap-5 sm:grid-cols-2">
              <TextField name="referenceVda" label={t('catalog.elements.referenceVda')} />
              <TextField name="referenceAiag" label={t('catalog.elements.referenceAiag')} />
            </div>
            <SelectField
              name="evidenceType"
              label={t('catalog.elements.evidenceType')}
              required
              options={EVIDENCE_TYPES.map((value) => ({
                value,
                label: t(`catalog.elements.evidenceTypeValues.${value}`),
              }))}
            />
            <JsonField
              name="valueSchema"
              label={t('catalog.elements.valueSchema')}
              description={t('catalog.elements.valueSchemaHint')}
            />
          </>
        )}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating?.code}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />
    </>
  )
}
