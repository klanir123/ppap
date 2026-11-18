import type { RowData } from '@tanstack/react-table'
import type { LucideIcon } from 'lucide-react'

/** Spring-style page as returned by all paginated master data endpoints. */
export interface PageData<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type ActiveFilter = 'true' | 'false' | 'all'

/** List state as kept in the URL search params (`?q=&active=&page=&size=&sort=field,asc`). */
export interface ListState {
  q?: string
  active?: ActiveFilter
  page: number
  size: number
  /** `field,asc` | `field,desc` */
  sort?: string
}

export interface RowAction {
  label: string
  icon?: LucideIcon
  onSelect: () => void
  destructive?: boolean
  disabled?: boolean
  /** Hide the action entirely (e.g. missing permission). */
  hidden?: boolean
}

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData extends RowData, TValue> {
    /** Translated column label (header, column menu, sort aria-label). */
    label?: string
    /** Server sort field; defaults to the column id. `false` = not sortable. */
    sortField?: string | false
    /** Can be hidden via the column menu (default true). */
    hideable?: boolean
    className?: string
    headerClassName?: string
  }
}
