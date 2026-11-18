import type { SortingState } from '@tanstack/react-table'
import { z } from 'zod'

import type { ListState } from './types'

export const PAGE_SIZES = [10, 25, 50, 100] as const
export const DEFAULT_PAGE_SIZE = 25

/**
 * Search-param schema for list routes (TanStack Router `validateSearch`).
 * Invalid values fall back to defaults instead of failing the route.
 */
export const listSearchSchema = z.object({
  q: z.string().optional().catch(undefined),
  active: z.enum(['true', 'false', 'all']).optional().catch(undefined),
  page: z.number().int().min(0).optional().catch(undefined),
  size: z.number().int().min(1).max(200).optional().catch(undefined),
  sort: z
    .string()
    .regex(/^[A-Za-z0-9_.]+,(asc|desc)$/)
    .optional()
    .catch(undefined),
})
export type ListSearch = z.infer<typeof listSearchSchema>

/** Applies defaults: active-only, first page, 25 rows. */
export function toListState(search: ListSearch, defaults: Partial<ListState> = {}): ListState {
  return {
    q: search.q || undefined,
    active: search.active ?? defaults.active ?? 'true',
    page: search.page ?? 0,
    size: search.size ?? defaults.size ?? DEFAULT_PAGE_SIZE,
    sort: search.sort ?? defaults.sort,
  }
}

/** Query params for the API: `active=all` is omitted. */
export function toQueryParams(state: ListState) {
  return {
    q: state.q,
    active: state.active === 'all' || state.active === undefined ? undefined : state.active === 'true',
    page: state.page,
    size: state.size,
    // Spring pageable: `sort` is an (exploded) array of `field,dir`
    sort: state.sort ? [state.sort] : undefined,
  }
}

export function sortToSorting(sort: string | undefined): SortingState {
  if (!sort) return []
  const [id, dir] = sort.split(',')
  return id ? [{ id, desc: dir === 'desc' }] : []
}

export function sortingToSort(sorting: SortingState): string | undefined {
  const first = sorting[0]
  return first ? `${first.id},${first.desc ? 'desc' : 'asc'}` : undefined
}

/** Strips default values so URLs stay short. */
export function compactSearch(state: Partial<ListState>): ListSearch {
  return {
    q: state.q || undefined,
    active: state.active === 'true' ? undefined : state.active,
    page: state.page ? state.page : undefined,
    size: state.size === DEFAULT_PAGE_SIZE ? undefined : state.size,
    sort: state.sort,
  }
}
