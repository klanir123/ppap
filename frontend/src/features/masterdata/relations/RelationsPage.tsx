import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link } from '@tanstack/react-router'
import type { ColumnDef } from '@tanstack/react-table'
import { ChevronDown, Eye, GitFork, Pencil, Plus, Power, Upload, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { DataTable, toQueryParams } from '@/components/data-table'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { usePermission } from '@/features/auth/current-user'

import { relationsApi, invalidateResource } from '../api'
import { relationRequest } from '../api/requests'
import type { Relation } from '../api/types'
import { importApi } from '../import/api'
import { ImportWizard } from '../import/ImportWizard'
import type { ImportEntity } from '../import/types'
import { ActiveBadge, MonoText } from '../shared/cells'
import { useCrudState } from '../shared/crud-state'
import { DeactivateDialog } from '../shared/DeactivateDialog'
import { useListState } from '../shared/list-state'
import { ReadOnlyHint } from '../shared/ReadOnlyHint'
import { ExchangeModeText, PartnerRoleChip } from './cells'
import { RelationFormSheet } from './RelationFormSheet'

const routeApi = getRouteApi('/masterdata/relationships/')
const imports = { relations: importApi('relations'), 'part-mappings': importApi('part-mappings') }

export function RelationsPage() {
  const { t } = useTranslation(['masterdata', 'forms'])
  const canWrite = usePermission('masterdata:write')
  const queryClient = useQueryClient()
  const navigate = routeApi.useNavigate()
  const { state, onStateChange } = useListState(
    routeApi.useSearch(),
    (search) => void navigate({ search: (prev) => ({ ...prev, ...search }), replace: true }),
  )
  const query = relationsApi.usePage(toQueryParams(state))
  const deactivate = relationsApi.useDeactivate()
  const reactivate = relationsApi.useReactivate(relationRequest)
  const crud = useCrudState<Relation>()
  const [importEntity, setImportEntity] = useState<Extract<ImportEntity, 'relations' | 'part-mappings'>>('relations')

  const columns = useMemo<ColumnDef<Relation, unknown>[]>(
    () => [
      {
        id: 'partnerName',
        header: t('relations.partner'),
        meta: { label: t('relations.partner'), hideable: false },
        cell: ({ row }) => (
          <Link
            to="/masterdata/relationships/$relationId"
            params={{ relationId: row.original.id }}
            className="flex flex-col rounded-sm underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            <span className="font-medium">{row.original.partnerName ?? row.original.partnerBpnl}</span>
            <MonoText className="text-muted-foreground">{row.original.partnerBpnl}</MonoText>
          </Link>
        ),
      },
      {
        id: 'partnerRole',
        header: t('relations.partnerRole'),
        meta: { label: t('relations.partnerRole') },
        cell: ({ row }) => <PartnerRoleChip role={row.original.partnerRole} />,
      },
      {
        id: 'exchangeMode',
        header: t('relations.exchangeMode'),
        meta: { label: t('relations.exchangeMode') },
        cell: ({ row }) => <ExchangeModeText mode={row.original.exchangeMode} />,
      },
      {
        id: 'mappingCount',
        header: t('relations.mappingCount'),
        meta: { label: t('relations.mappingCount'), sortField: false, className: 'tabular' },
        cell: ({ row }) => row.original.mappingCount ?? '–',
      },
      {
        id: 'active',
        header: t('common.status'),
        meta: { label: t('common.status') },
        cell: ({ row }) => <ActiveBadge active={row.original.active} />,
      },
    ],
    [t],
  )

  return (
    <div className="space-y-6">
      <PageHeader title={t('relations.title')} description={t('relations.description')} />
      {canWrite ? null : <ReadOnlyHint />}
      <DataTable<Relation>
        id="relations"
        label={t('relations.title')}
        columns={columns}
        page={query.data}
        state={state}
        onStateChange={onStateChange}
        isLoading={query.isPending}
        isFetching={query.isFetching}
        error={query.error}
        onRetry={() => void query.refetch()}
        getRowId={(row) => row.id}
        getRowLabel={(row) => row.partnerName ?? row.partnerBpnl}
        searchPlaceholder={t('relations.search')}
        rowActions={(row) => [
          {
            label: t('common.view'),
            icon: Eye,
            onSelect: () =>
              void navigate({
                to: '/masterdata/relationships/$relationId',
                params: { relationId: row.id },
                search: {},
              }),
          },
          { label: t('common.edit'), icon: Pencil, hidden: !canWrite, onSelect: () => crud.openEdit(row) },
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
                onSuccess: () => toast.success(t('common.reactivated', { name: row.partnerName ?? row.partnerBpnl })),
              }),
          },
        ]}
        toolbar={
          canWrite ? (
            <>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="outline" size="sm">
                    <Upload aria-hidden="true" />
                    {t('common.import')}
                    <ChevronDown aria-hidden="true" data-icon="inline-end" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {(['relations', 'part-mappings'] as const).map((entity) => (
                    <DropdownMenuItem
                      key={entity}
                      onSelect={() => {
                        setImportEntity(entity)
                        crud.setImportOpen(true)
                      }}
                    >
                      {t(entity === 'relations' ? 'relations.title' : 'partMappings.title')}
                    </DropdownMenuItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
              <Button size="sm" onClick={crud.openNew}>
                <Plus aria-hidden="true" />
                {t('relations.new')}
              </Button>
            </>
          ) : null
        }
        emptyState={{
          icon: GitFork,
          title: t('relations.empty'),
          description: t('relations.emptyDescription'),
          action: canWrite ? (
            <Button size="sm" onClick={crud.openNew}>
              <Plus aria-hidden="true" />
              {t('relations.new')}
            </Button>
          ) : undefined,
        }}
      />

      <RelationFormSheet
        editing={crud.editing}
        onClose={crud.closeEditor}
        onReloaded={crud.setEditing}
        readOnly={!canWrite}
      />

      <DeactivateDialog
        name={crud.deactivating ? (crud.deactivating.partnerName ?? crud.deactivating.partnerBpnl) : undefined}
        onCancel={() => crud.setDeactivating(null)}
        onConfirm={() => deactivate.mutateAsync({ id: crud.deactivating!.id, version: crud.deactivating!.version })}
      />

      <ImportWizard
        open={crud.importOpen}
        onOpenChange={crud.setImportOpen}
        entity={importEntity}
        entityLabel={t(importEntity === 'relations' ? 'relations.title' : 'partMappings.title')}
        {...imports[importEntity]}
        onCompleted={() => {
          void invalidateResource(queryClient, relationsApi.keyPrefix)
        }}
      />
    </div>
  )
}
