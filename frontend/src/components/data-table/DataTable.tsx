import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
  type VisibilityState,
} from '@tanstack/react-table'
import { ArrowDown, ArrowUp, ArrowUpDown, Columns3, SearchX, TriangleAlert, type LucideIcon } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

import { DataTablePagination } from './DataTablePagination'
import { sortingToSort, sortToSorting } from './list-search'
import { RowActionsMenu } from './RowActionsMenu'
import { SearchInput } from './SearchInput'
import type { ActiveFilter, ListState, PageData, RowAction } from './types'

export interface DataTableProps<T> {
  /** Stable id, used to remember column visibility per table. */
  id: string
  /** Accessible name of the table. */
  label: string
  columns: ColumnDef<T, unknown>[]
  /**
   * Server mode: a page from the API (`content` = current page only).
   * Client mode: pass `rows` instead – search, active filter, sorting and paging happen locally.
   */
  page?: PageData<T>
  rows?: T[]
  state: ListState
  onStateChange: (patch: Partial<ListState>) => void
  getRowId: (row: T) => string
  /** Human readable row name for action labels ("Aktionen für D10-4711"). */
  getRowLabel: (row: T) => string
  rowActions?: (row: T) => RowAction[]
  isLoading?: boolean
  /** Refetching in the background (keeps old rows, dims them). */
  isFetching?: boolean
  error?: unknown
  onRetry?: () => void
  searchPlaceholder?: string
  /** Show the Aktiv/Inaktiv/Alle filter (default true). */
  activeFilter?: boolean
  /** Client mode: fields searched by `q` (default: all string values). */
  searchFields?: (keyof T & string)[]
  emptyState: { icon: LucideIcon; title: string; description?: string; action?: ReactNode }
  /** Extra toolbar content (right side), e.g. import / create buttons. */
  toolbar?: ReactNode
  /** Client mode only: hide the pagination footer (e.g. short catalogue lists). */
  hidePagination?: boolean
  /** Show the search field (default true). */
  search?: boolean
  /** Extra filter controls (left side of the toolbar, after search). */
  filters?: ReactNode
  /** Custom filters are active (shows "no results" instead of the empty state). */
  filtered?: boolean
  /** Resets search + custom filters (default: search and active filter). */
  onResetFilters?: () => void
}

const VISIBILITY_KEY = (id: string) => `ppap.table.${id}.columns`

function loadVisibility(id: string): VisibilityState {
  try {
    const raw = window.localStorage.getItem(VISIBILITY_KEY(id))
    return raw ? (JSON.parse(raw) as VisibilityState) : {}
  } catch {
    return {}
  }
}

function saveVisibility(id: string, state: VisibilityState) {
  try {
    window.localStorage.setItem(VISIBILITY_KEY(id), JSON.stringify(state))
  } catch {
    // storage unavailable – visibility only lasts for this page view
  }
}

function compareValues(a: unknown, b: unknown): number {
  if (a == null && b == null) return 0
  if (a == null) return 1
  if (b == null) return -1
  if (typeof a === 'number' && typeof b === 'number') return a - b
  return String(a).localeCompare(String(b), undefined, { numeric: true, sensitivity: 'base' })
}

/** Local search/filter/sort/paging for client mode (catalogue lists). */
function processClientRows<T>(rows: T[], state: ListState, searchFields?: (keyof T & string)[]): PageData<T> {
  let result = rows
  if (state.active && state.active !== 'all') {
    const wanted = state.active === 'true'
    result = result.filter(
      (r) => (r as { active?: boolean }).active === undefined || (r as { active?: boolean }).active === wanted,
    )
  }
  const q = state.q?.trim().toLowerCase()
  if (q) {
    result = result.filter((row) => {
      const record = row as Record<string, unknown>
      const values = searchFields ? searchFields.map((f) => record[f]) : Object.values(record)
      return values.some((v) => typeof v === 'string' && v.toLowerCase().includes(q))
    })
  }
  const [sort] = sortToSorting(state.sort)
  if (sort) {
    result = [...result].sort((a, b) => {
      const cmp = compareValues((a as Record<string, unknown>)[sort.id], (b as Record<string, unknown>)[sort.id])
      return sort.desc ? -cmp : cmp
    })
  }
  const totalElements = result.length
  const totalPages = Math.max(1, Math.ceil(totalElements / state.size))
  const page = Math.min(state.page, totalPages - 1)
  return {
    content: result.slice(page * state.size, (page + 1) * state.size),
    page,
    size: state.size,
    totalElements,
    totalPages,
  }
}

/**
 * Data table on TanStack Table with server-side (or client-side) paging, sorting and search.
 * All list state is controlled by the caller – typically synced to the route search params.
 */
export function DataTable<T>(props: DataTableProps<T>) {
  const {
    id,
    label,
    columns,
    state,
    onStateChange,
    getRowId,
    getRowLabel,
    rowActions,
    isLoading,
    isFetching,
    error,
    onRetry,
    searchPlaceholder,
    activeFilter = true,
    emptyState,
    toolbar,
    search = true,
    filters,
    onResetFilters,
  } = props
  const { t } = useTranslation(['table', 'common'])
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(() => loadVisibility(id))

  const pageData: PageData<T> | undefined = useMemo(
    () => (props.page ? props.page : props.rows ? processClientRows(props.rows, state, props.searchFields) : undefined),
    [props.page, props.rows, props.searchFields, state],
  )

  const allColumns = useMemo<ColumnDef<T, unknown>[]>(() => {
    if (!rowActions) return columns
    return [
      ...columns,
      {
        id: '__actions',
        enableSorting: false,
        enableHiding: false,
        header: () => <span className="sr-only">{t('actions')}</span>,
        cell: ({ row }) => <RowActionsMenu actions={rowActions(row.original)} rowLabel={getRowLabel(row.original)} />,
        meta: { className: 'w-12 text-right', headerClassName: 'w-12', sortField: false, hideable: false },
      },
    ]
  }, [columns, rowActions, getRowLabel, t])

  const sorting = useMemo(() => sortToSorting(state.sort), [state.sort])

  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack Table returns non-memoizable functions
  const table = useReactTable<T>({
    data: pageData?.content ?? [],
    columns: allColumns,
    getRowId,
    getCoreRowModel: getCoreRowModel(),
    manualSorting: true,
    manualPagination: true,
    manualFiltering: true,
    enableSortingRemoval: true,
    rowCount: pageData?.totalElements ?? 0,
    state: { sorting, columnVisibility },
    onSortingChange: (updater) => {
      const next: SortingState = typeof updater === 'function' ? updater(sorting) : updater
      onStateChange({ sort: sortingToSort(next), page: 0 })
    },
    onColumnVisibilityChange: (updater) => {
      setColumnVisibility((prev) => {
        const next = typeof updater === 'function' ? updater(prev) : updater
        saveVisibility(id, next)
        return next
      })
    },
  })

  const visibleColumnCount = table.getVisibleLeafColumns().length
  const filtered =
    Boolean(props.filtered) ||
    Boolean(state.q) ||
    (activeFilter && state.active !== undefined && state.active !== 'true')
  const showSkeleton = isLoading && !pageData
  const rows = table.getRowModel().rows
  const hideableColumns = table
    .getAllLeafColumns()
    .filter((c) => c.getCanHide() && c.columnDef.meta?.hideable !== false)

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          {search ? (
            <SearchInput
              value={state.q}
              placeholder={searchPlaceholder}
              onCommit={(q) => onStateChange({ q, page: 0 })}
            />
          ) : null}
          {activeFilter ? (
            <Select
              value={state.active ?? 'true'}
              onValueChange={(value) => onStateChange({ active: value as ActiveFilter, page: 0 })}
            >
              <SelectTrigger size="sm" className="w-full sm:w-36" aria-label={t('activeFilter.label')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(['true', 'false', 'all'] as const).map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`activeFilter.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null}
          {filters}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {hideableColumns.length > 0 ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" aria-label={t('toggleColumns')}>
                  <Columns3 aria-hidden="true" />
                  <span className="hidden md:inline">{t('columns')}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="min-w-48">
                <DropdownMenuLabel>{t('toggleColumns')}</DropdownMenuLabel>
                <DropdownMenuSeparator />
                {hideableColumns.map((column) => (
                  <DropdownMenuCheckboxItem
                    key={column.id}
                    checked={column.getIsVisible()}
                    onCheckedChange={(checked) => column.toggleVisibility(checked)}
                    onSelect={(e) => e.preventDefault()}
                  >
                    {column.columnDef.meta?.label ?? column.id}
                  </DropdownMenuCheckboxItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          ) : null}
          {toolbar}
        </div>
      </div>

      <div className="relative rounded-lg border bg-card">
        <div className="relative max-h-[min(70svh,48rem)] overflow-auto rounded-lg">
          <table
            aria-label={label}
            aria-busy={isLoading || isFetching ? true : undefined}
            className={cn('w-full caption-bottom text-sm', isFetching && !isLoading && 'opacity-70 transition-opacity')}
          >
            <TableHeader className="sticky top-0 z-10 bg-muted/80 backdrop-blur supports-[backdrop-filter]:bg-muted/60">
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="hover:bg-transparent">
                  {headerGroup.headers.map((header) => {
                    const meta = header.column.columnDef.meta
                    // server-side sorting: every column is sortable unless it opts out (sortField: false)
                    const sortable = meta?.sortField !== false && header.column.columnDef.enableSorting !== false
                    const sorted = sorting.find((s) => s.id === (meta?.sortField || header.column.id))
                    const columnLabel = meta?.label ?? header.column.id
                    return (
                      <TableHead
                        key={header.id}
                        scope="col"
                        aria-sort={sorted ? (sorted.desc ? 'descending' : 'ascending') : undefined}
                        className={cn(
                          'h-10 whitespace-nowrap text-xs font-medium text-muted-foreground',
                          meta?.headerClassName,
                        )}
                      >
                        {header.isPlaceholder ? null : sortable ? (
                          <button
                            type="button"
                            className="-ml-2 inline-flex h-7 items-center gap-1 rounded-md px-2 hover:bg-accent hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
                            onClick={() => {
                              const field = meta?.sortField || header.column.id
                              const next = !sorted ? `${field},asc` : !sorted.desc ? `${field},desc` : undefined
                              onStateChange({ sort: next, page: 0 })
                            }}
                            aria-label={t('sortBy', { column: columnLabel })}
                          >
                            {flexRender(header.column.columnDef.header, header.getContext())}
                            {sorted ? (
                              sorted.desc ? (
                                <ArrowDown aria-hidden="true" className="size-3.5" />
                              ) : (
                                <ArrowUp aria-hidden="true" className="size-3.5" />
                              )
                            ) : (
                              <ArrowUpDown aria-hidden="true" className="size-3.5 opacity-40" />
                            )}
                          </button>
                        ) : (
                          flexRender(header.column.columnDef.header, header.getContext())
                        )}
                      </TableHead>
                    )
                  })}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {showSkeleton ? (
                Array.from({ length: Math.min(state.size, 8) }, (_, i) => (
                  <TableRow key={`skeleton-${i}`} aria-hidden="true">
                    {table.getVisibleLeafColumns().map((column) => (
                      <TableCell key={column.id}>
                        <Skeleton className={cn('h-4', column.id === '__actions' ? 'ml-auto w-6' : 'w-3/4')} />
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : error && !pageData ? (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={visibleColumnCount} className="p-6">
                    <EmptyState
                      icon={TriangleAlert}
                      title={t('error')}
                      action={
                        onRetry ? (
                          <Button variant="outline" size="sm" onClick={onRetry}>
                            {t('common:retry')}
                          </Button>
                        ) : undefined
                      }
                    />
                  </TableCell>
                </TableRow>
              ) : rows.length === 0 ? (
                <TableRow className="hover:bg-transparent">
                  <TableCell colSpan={visibleColumnCount} className="p-6 whitespace-normal">
                    {filtered ? (
                      <EmptyState
                        icon={SearchX}
                        title={t('noResults')}
                        description={t('noResultsDescription')}
                        action={
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() =>
                              onResetFilters
                                ? onResetFilters()
                                : onStateChange({ q: undefined, active: 'true', page: 0 })
                            }
                          >
                            {t('resetFilters')}
                          </Button>
                        }
                      />
                    ) : (
                      <EmptyState {...emptyState} />
                    )}
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.getVisibleCells().map((cell) => (
                      <TableCell key={cell.id} className={cell.column.columnDef.meta?.className}>
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              )}
            </TableBody>
          </table>
        </div>
      </div>

      {pageData && !props.hidePagination && pageData.totalElements > 0 ? (
        <DataTablePagination
          page={pageData.page}
          size={pageData.size}
          totalElements={pageData.totalElements}
          totalPages={pageData.totalPages}
          onPageChange={(page) => onStateChange({ page })}
          onSizeChange={(size) => onStateChange({ size, page: 0 })}
        />
      ) : null}
    </div>
  )
}
