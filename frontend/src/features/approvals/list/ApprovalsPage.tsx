import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { Columns3, FileCheck2, PenLine, Plus, Rows3, TriangleAlert } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DataTable, type ListState } from '@/components/data-table'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { usePermission } from '@/features/auth/current-user'

import { useCasePage, type ApprovalCase } from '../api'
import { ManualRequestDialog } from '../manual/ManualRequestDialog'
import { boardStatuses } from '../model/kanban'
import { ApprovalFilters } from './ApprovalFilters'
import {
  BOARD_PAGE_SIZE,
  DEFAULT_SORT,
  hasFilters,
  RESET_FILTERS,
  toCaseListParams,
  type ApprovalsSearch,
  type ApprovalView,
} from './approvals-search'
import { caseColumns } from './columns'
import { KanbanBoard } from './KanbanBoard'

const routeApi = getRouteApi('/approvals/')

export function ApprovalsPage() {
  const { t, i18n } = useTranslation('approvals')
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const goTo = useNavigate()
  const canRequest = usePermission('approvals:request')
  const canSupply = usePermission('approvals:supply')
  const [manualOpen, setManualOpen] = useState(false)
  const view: ApprovalView = search.view ?? 'table'
  const params = toCaseListParams(search, view)
  const query = useCasePage(params)
  const columns = useMemo(() => caseColumns(t, i18n.resolvedLanguage), [t, i18n.resolvedLanguage])

  const update = (patch: Partial<ApprovalsSearch>) =>
    void navigate({ search: (prev) => ({ ...prev, ...patch }), replace: true })

  const state: ListState = {
    q: search.q,
    page: search.page ?? 0,
    size: params.size!,
    sort: search.sort ?? DEFAULT_SORT,
  }
  const filtered = hasFilters(search)

  const actions = (
    <>
      {canSupply ? (
        <Button variant="outline" onClick={() => setManualOpen(true)}>
          <PenLine aria-hidden="true" />
          {t('list.manualCapture')}
        </Button>
      ) : null}
      {canRequest ? (
        <Button asChild>
          <Link to="/approvals/new">
            <Plus aria-hidden="true" />
            {t('list.request')}
          </Link>
        </Button>
      ) : null}
    </>
  )

  const viewToggle = (
    <ToggleGroup
      type="single"
      variant="outline"
      size="sm"
      value={view}
      onValueChange={(v) => v && update({ view: v === 'table' ? undefined : (v as ApprovalView), page: undefined })}
      aria-label={t('list.view')}
    >
      <ToggleGroupItem value="table" aria-label={t('list.viewTable')}>
        <Rows3 aria-hidden="true" />
        <span className="hidden md:inline">{t('list.viewTable')}</span>
      </ToggleGroupItem>
      <ToggleGroupItem value="board" aria-label={t('list.viewBoard')}>
        <Columns3 aria-hidden="true" />
        <span className="hidden md:inline">{t('list.viewBoard')}</span>
      </ToggleGroupItem>
    </ToggleGroup>
  )

  const emptyState = {
    icon: FileCheck2,
    title: t('list.empty'),
    description: canRequest
      ? t('list.emptyCustomer')
      : canSupply
        ? t('list.emptySupplier')
        : t('list.emptyDescription'),
    action: canRequest || canSupply ? <div className="flex flex-wrap justify-center gap-2">{actions}</div> : undefined,
  }

  return (
    <div className="space-y-6">
      <PageHeader title={t('list.title')} description={t('list.description')} actions={actions} />

      {view === 'table' ? (
        <DataTable<ApprovalCase>
          id="approval-cases"
          label={t('list.title')}
          columns={columns}
          page={query.data}
          state={state}
          onStateChange={(patch) =>
            update({
              q: 'q' in patch ? patch.q : search.q,
              page: patch.page || undefined,
              size: patch.size,
              sort: 'sort' in patch ? (patch.sort === DEFAULT_SORT ? undefined : patch.sort) : search.sort,
            })
          }
          activeFilter={false}
          filters={<ApprovalFilters search={search} onChange={update} />}
          filtered={filtered}
          onResetFilters={() => update(RESET_FILTERS)}
          toolbar={viewToggle}
          isLoading={query.isPending}
          isFetching={query.isFetching}
          error={query.error}
          onRetry={() => void query.refetch()}
          getRowId={(row) => row.id}
          getRowLabel={(row) => row.caseNumber}
          searchPlaceholder={t('list.search')}
          rowActions={(row) => [
            {
              label: t('list.open'),
              icon: FileCheck2,
              onSelect: () => void goTo({ to: '/approvals/$caseId', params: { caseId: row.id } }),
            },
          ]}
          emptyState={emptyState}
        />
      ) : (
        <div className="space-y-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-1 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
              <ApprovalFilters search={search} onChange={update} withSearch />
            </div>
            <div className="flex items-center gap-2">{viewToggle}</div>
          </div>
          {query.isError && !query.data ? (
            <EmptyState
              icon={TriangleAlert}
              title={t('list.loadError')}
              action={
                <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
                  {t('list.retry')}
                </Button>
              }
            />
          ) : !query.isPending && query.data?.totalElements === 0 && !filtered ? (
            <EmptyState {...emptyState} />
          ) : (
            <>
              {query.data && query.data.totalElements > BOARD_PAGE_SIZE ? (
                <p role="status" className="text-xs text-muted-foreground">
                  {t('board.truncated', { shown: BOARD_PAGE_SIZE, total: query.data.totalElements })}
                </p>
              ) : null}
              <KanbanBoard
                cases={query.data?.content ?? []}
                statuses={boardStatuses(search.status)}
                isLoading={query.isPending}
              />
            </>
          )}
        </div>
      )}

      {canSupply ? <ManualRequestDialog open={manualOpen} onOpenChange={setManualOpen} /> : null}
    </div>
  )
}
