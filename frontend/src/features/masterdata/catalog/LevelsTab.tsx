import { useQueryClient } from '@tanstack/react-query'
import type { ColumnDef } from '@tanstack/react-table'
import { Layers, Pencil, Plus, Power } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { updateSubmissionLevel } from '@/api/generated/endpoints/catalog/catalog'
import { DataTable, type ListState } from '@/components/data-table'
import { FormSheet, SelectField } from '@/components/forms'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

import { elementsApi, ifMatch, invalidateResource, levelsApi } from '../api'
import { LEVEL_SCHEMES, type CatalogLevel } from '../api/types'
import { ActiveBadge } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { localizedName } from '../shared/list-state'
import { levelDefaults, levelSchema, nextSortOrder, toLevelInput } from './catalog-schema'
import { CatalogEntryFields } from './CatalogEntryFields'
import { LevelMatrix, type MatrixElement, type MatrixLevel } from './LevelMatrix'

export function LevelsTab({ canWrite }: { canWrite: boolean }) {
  const { t, i18n } = useTranslation(['masterdata', 'forms'])
  const lng = i18n.resolvedLanguage
  const queryClient = useQueryClient()
  const levels = levelsApi.useAll({})
  const elements = elementsApi.useAll({})
  const save = levelsApi.useSave()
  const deactivate = levelsApi.useDeactivate()
  const crud = useCrudState<CatalogLevel>()
  const editing = crud.current
  const { openEdit } = crud
  const [listState, setListState] = useState<ListState>({ page: 0, size: 100, active: 'true', sort: 'sortOrder,asc' })

  const activeLevels = useMemo(
    () => (levels.data ?? []).filter((l) => l.active).sort((a, b) => a.sortOrder - b.sortOrder),
    [levels.data],
  )
  const matrixLevels: MatrixLevel[] = activeLevels.map((l) => ({
    id: l.id,
    code: l.code,
    name: localizedName(l, lng),
    version: l.version,
    requirements: l.requirements ?? {},
  }))
  // active elements, plus inactive ones still referenced by a level (AK-02-3: stay visible)
  const referenced = new Set(activeLevels.flatMap((l) => Object.keys(l.requirements ?? {})))
  const matrixElements: MatrixElement[] = (elements.data ?? [])
    .filter((e) => e.active || referenced.has(e.code))
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((e) => ({ code: e.code, name: localizedName(e, lng), active: e.active }))

  async function saveMatrix(level: MatrixLevel, requirements: CatalogLevel['requirements']) {
    const full = activeLevels.find((l) => l.id === level.id)
    if (!full) return
    const { id, version, createdAt: _c, updatedAt: _u, ...rest } = full
    await updateSubmissionLevel(id, { ...rest, requirements }, ifMatch(version))
    await invalidateResource(queryClient, levelsApi.keyPrefix)
  }

  const columns = useMemo<ColumnDef<CatalogLevel, unknown>[]>(
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
        id: 'scheme',
        header: t('catalog.levels.scheme'),
        meta: { label: t('catalog.levels.scheme') },
        cell: ({ row }) => t(`catalog.levels.schemeValues.${row.original.scheme}`),
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
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>{t('catalog.levels.matrixTitle')}</CardTitle>
          <CardDescription>{t('catalog.levels.matrixDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          {levels.isPending || elements.isPending ? (
            <div className="space-y-2" aria-busy="true">
              {Array.from({ length: 6 }, (_, i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : (
            <LevelMatrix
              elements={matrixElements}
              levels={matrixLevels}
              onSaveLevel={(level, requirements) => saveMatrix(level, requirements)}
              readOnly={!canWrite}
            />
          )}
        </CardContent>
      </Card>

      <section aria-labelledby="levels-heading" className="space-y-3">
        <h2 id="levels-heading" className="text-lg font-semibold tracking-tight">
          {t('catalog.levels.manage')}
        </h2>
        <DataTable<CatalogLevel>
          id="catalog-levels"
          label={t('catalog.tabs.levels')}
          columns={columns}
          rows={levels.data}
          state={listState}
          onStateChange={(patch) => setListState((s) => ({ ...s, ...patch }))}
          isLoading={levels.isPending}
          error={levels.error}
          onRetry={() => void levels.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.code}
          searchFields={['code', 'nameDe', 'nameEn']}
          hidePagination
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
              <Button size="sm" onClick={crud.openNew}>
                <Plus aria-hidden="true" />
                {t('catalog.levels.new')}
              </Button>
            ) : null
          }
          emptyState={{
            icon: Layers,
            title: t('catalog.levels.empty'),
            description: t('catalog.levels.emptyDescription'),
          }}
        />
      </section>

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('catalog.levels.new') : t('catalog.levels.editTitle')}
        description={editing?.code}
        schema={levelSchema}
        defaultValues={levelDefaults(editing, nextSortOrder(levels.data))}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void levelsApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({ id: editing?.id, version: editing?.version, input: toLevelInput(values, editing) })
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.code }))
        }}
      >
        {() => (
          <>
            <CatalogEntryFields isNew={crud.isNew} />
            <SelectField
              name="scheme"
              label={t('catalog.levels.scheme')}
              required
              options={LEVEL_SCHEMES.map((value) => ({ value, label: t(`catalog.levels.schemeValues.${value}`) }))}
            />
          </>
        )}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating?.code}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />
    </div>
  )
}
