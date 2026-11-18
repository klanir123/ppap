import { compactSearch, toListState, type ListSearch, type ListState } from '@/components/data-table'

/**
 * Binds DataTable list state to route search params.
 * `navigate` receives the next (compacted) list search; the caller merges it into the route search.
 */
export function useListState(search: ListSearch, navigate: (next: ListSearch) => void, defaults?: Partial<ListState>) {
  const state = toListState(search, defaults)
  const onStateChange = (patch: Partial<ListState>) => navigate(compactSearch({ ...state, ...patch }))
  return { state, onStateChange }
}

export function localizedName<T extends { nameDe: string; nameEn?: string }>(row: T, lng: string | undefined): string {
  return lng === 'en' && row.nameEn ? row.nameEn : row.nameDe
}
