import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { ArrowLeft, Link2, Pencil, Plus, SearchX, Trash2 } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, toQueryParams } from '@/components/data-table'
import { EmptyState } from '@/components/EmptyState'
import { FormSheet, TextField } from '@/components/forms'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { usePermission } from '@/features/auth/current-user'

import { invalidateResource, partMappingsApi, relationsApi } from '../api'
import type { PartMapping, Relation } from '../api/types'
import { AuditTrail } from '../shared/AuditTrail'
import { ActiveBadge, MonoText, Muted } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { ExchangeModeText, PartnerRoleChip } from './cells'
import { RelationFormSheet } from './RelationFormSheet'
import { partMappingDefaults, partMappingSchema, toPartMappingInput } from './relation-schema'

const routeApi = getRouteApi('/masterdata/relationships/$relationId')

export function RelationDetailPage() {
  const { t } = useTranslation(['masterdata', 'forms'])
  const { relationId } = routeApi.useParams()
  const canWrite = usePermission('masterdata:write')
  const relation = relationsApi.useOne(relationId)
  const [editingRelation, setEditingRelation] = useState<Relation | null>(null)

  if (relation.isPending) {
    return (
      <div className="space-y-6" aria-busy="true">
        <Skeleton className="h-8 w-80" />
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (relation.isError || !relation.data) {
    return (
      <EmptyState
        icon={SearchX}
        title={t('relations.notFound')}
        action={
          <Button variant="outline" size="sm" asChild>
            <Link to="/masterdata/relationships">
              <ArrowLeft aria-hidden="true" />
              {t('common.back')}
            </Link>
          </Button>
        }
      />
    )
  }
  const data = relation.data

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-2">
          <Button variant="ghost" size="sm" className="-ml-2" asChild>
            <Link to="/masterdata/relationships">
              <ArrowLeft aria-hidden="true" />
              {t('relations.title')}
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">
            {t('relations.detailTitle', { name: data.partnerName ?? data.partnerBpnl })}
          </h1>
        </div>
        {canWrite ? (
          <Button variant="outline" size="sm" onClick={() => setEditingRelation(data)}>
            <Pencil aria-hidden="true" />
            {t('common.edit')}
          </Button>
        ) : null}
      </div>

      <Card>
        <CardContent>
          <dl className="flex flex-wrap gap-x-12 gap-y-4 text-sm">
            <div className="space-y-1">
              <dt className="text-xs text-muted-foreground">{t('relations.partnerBpnl')}</dt>
              <dd>
                <MonoText>{data.partnerBpnl}</MonoText>
              </dd>
            </div>
            <div className="space-y-1">
              <dt className="text-xs text-muted-foreground">{t('relations.partnerRole')}</dt>
              <dd>
                <PartnerRoleChip role={data.partnerRole} />
              </dd>
            </div>
            <div className="space-y-1">
              <dt className="text-xs text-muted-foreground">{t('relations.exchangeMode')}</dt>
              <dd>
                <ExchangeModeText mode={data.exchangeMode} />
              </dd>
            </div>
            <div className="space-y-1">
              <dt className="text-xs text-muted-foreground">{t('relations.policyProfile')}</dt>
              <dd>{data.policyProfile ? <MonoText>{data.policyProfile}</MonoText> : <Muted>–</Muted>}</dd>
            </div>
            <div className="space-y-1">
              <dt className="text-xs text-muted-foreground">{t('common.status')}</dt>
              <dd>
                <ActiveBadge active={data.active} />
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      {canWrite ? null : <ReadOnlyHint />}
      <PartMappingsSection relationId={relationId} canWrite={canWrite} />

      <Card>
        <CardContent>
          <AuditTrail objectType="RELATION" objectId={data.id} />
        </CardContent>
      </Card>

      <RelationFormSheet
        editing={editingRelation}
        onClose={() => setEditingRelation(null)}
        onReloaded={setEditingRelation}
        readOnly={!canWrite}
      />
    </div>
  )
}

function PartMappingsSection({ relationId, canWrite }: { relationId: string; canWrite: boolean }) {
  const { t } = useTranslation(['masterdata', 'forms'])
  const queryClient = useQueryClient()
  const navigate = routeApi.useNavigate()
  const { state, onStateChange } = useListState(
    routeApi.useSearch(),
    (search) => void navigate({ search: (prev) => ({ ...prev, ...search }), replace: true }),
    { active: 'all' },
  )
  const mappingsApi = useMemo(() => partMappingsApi(relationId), [relationId])
  const { active: _active, ...params } = toQueryParams(state)
  const query = mappingsApi.usePage(params)
  const save = mappingsApi.useSave()
  const remove = mappingsApi.useDeactivate()
  const crud = useCrudState<PartMapping>()
  const editing = crud.current
  const { openEdit } = crud
  // mappingCount of the relation changes as well (same `/api/v1/relations` key prefix)
  const refreshRelation = () => void invalidateResource(queryClient, relationsApi.keyPrefix)

  const columns = useMemo<ColumnDef<PartMapping, unknown>[]>(
    () => [
      {
        id: 'partNumber',
        header: t('partMappings.partNumber'),
        meta: { label: t('partMappings.partNumber'), hideable: false },
        cell: ({ row }) => (
          <button
            type="button"
            className="rounded-sm font-mono text-xs font-medium whitespace-nowrap underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
            onClick={() => openEdit(row.original)}
          >
            {row.original.partNumber}
          </button>
        ),
      },
      {
        id: 'partNameDe',
        accessorKey: 'partNameDe',
        header: t('partMappings.partNameDe'),
        meta: { label: t('partMappings.partNameDe'), sortField: false },
      },
      {
        id: 'partnerPartNumber',
        header: t('partMappings.partnerPartNumber'),
        meta: { label: t('partMappings.partnerPartNumber'), hideable: false },
        cell: ({ row }) => <MonoText>{row.original.partnerPartNumber}</MonoText>,
      },
      {
        id: 'ownSiteBpns',
        header: t('partMappings.ownSiteBpns'),
        meta: { label: t('partMappings.ownSiteBpns') },
        cell: ({ row }) =>
          row.original.ownSiteBpns ? <MonoText>{row.original.ownSiteBpns}</MonoText> : <Muted>–</Muted>,
      },
      {
        id: 'partnerSiteBpns',
        header: t('partMappings.partnerSiteBpns'),
        meta: { label: t('partMappings.partnerSiteBpns') },
        cell: ({ row }) =>
          row.original.partnerSiteBpns ? <MonoText>{row.original.partnerSiteBpns}</MonoText> : <Muted>–</Muted>,
      },
    ],
    [t, openEdit],
  )

  return (
    <section aria-labelledby="part-mappings-heading" className="space-y-3">
      <div>
        <h2 id="part-mappings-heading" className="text-lg font-semibold tracking-tight">
          {t('partMappings.title')}
        </h2>
        <p className="text-sm text-muted-foreground">{t('partMappings.description')}</p>
      </div>
      <DataTable<PartMapping>
        id="part-mappings"
        label={t('partMappings.title')}
        columns={columns}
        page={query.data}
        state={state}
        onStateChange={onStateChange}
        activeFilter={false}
        isLoading={query.isPending}
        isFetching={query.isFetching}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.partNumber ?? row.partnerPartNumber}
        searchPlaceholder={t('partMappings.search')}
        rowActions={(row) => [
          { label: canWrite ? t('common.edit') : t('common.view'), icon: Pencil, onSelect: () => crud.openEdit(row) },
          {
            label: t('partMappings.remove'),
            icon: Trash2,
            destructive: true,
            hidden: !canWrite,
            onSelect: () => crud.setDeactivating(row),
          },
        ]}
        toolbar={
          canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('partMappings.new')}
            </Button>
          ) : null
        }
        emptyState={{
          icon: Link2,
          title: t('partMappings.empty'),
          description: t('partMappings.emptyDescription'),
          action: canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('partMappings.new')}
            </Button>
          ) : undefined,
        }}
      />

      <FormSheet
        open={crud.editing !== null}
        onOpenChange={(open) => !open && crud.closeEditor()}
        title={crud.isNew ? t('partMappings.new') : t('partMappings.editTitle')}
        description={editing?.partNameDe}
        schema={partMappingSchema}
        defaultValues={partMappingDefaults(editing)}
        formKey={editing ? `${editing.id}:${editing.version}` : 'new'}
        readOnly={!canWrite}
        submitLabel={crud.isNew ? t('forms:create') : undefined}
        onReload={() => editing && void mappingsApi.fetchOne(editing.id).then(crud.setEditing)}
        onSubmit={async (values) => {
          await save.mutateAsync({
            id: editing?.id,
            version: editing?.version,
            input: toPartMappingInput(values, editing),
          })
          refreshRelation()
          toast.success(t(crud.isNew ? 'common.created' : 'common.saved', { name: values.partNumber }))
        }}
      >
        {() => (
          <>
            <TextField name="partNumber" label={t('partMappings.partNumber')} required mono disabled={!crud.isNew} />
            <TextField name="partnerPartNumber" label={t('partMappings.partnerPartNumber')} required mono />
            <div className="grid gap-5 sm:grid-cols-2">
              <TextField name="ownSiteBpns" label={t('partMappings.ownSiteBpns')} mono uppercase placeholder="BPNS…" />
              <TextField
                name="partnerSiteBpns"
                label={t('partMappings.partnerSiteBpns')}
                mono
                uppercase
                placeholder="BPNS…"
              />
            </div>
            {editing ? (
              <>
                <Separator />
                <AuditTrail objectType="PART_MAPPING" objectId={editing.id} />
              </>
            ) : null}
          </>
        )}
      </FormSheet>

      <DeactivateDialog
        name={crud.deactivating ? (crud.deactivating.partNumber ?? crud.deactivating.partnerPartNumber) : undefined}
        title={t('partMappings.removeTitle')}
        description={t('partMappings.removeDescription')}
        confirmLabel={t('partMappings.remove')}
        successMessage={t('partMappings.removed')}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() =>
          remove.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version }).then(refreshRelation)
        }
      />
    </section>
  )
}
