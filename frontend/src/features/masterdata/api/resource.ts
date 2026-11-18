import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import type { PageData } from '@/components/data-table'

type RequestOptions = RequestInit | undefined
type IfMatchHeaders = { 'If-Match'?: string }

/** `If-Match: "<version>"` for optimistic locking (412 / VERSION_CONFLICT on mismatch). */
export function ifMatch(version: number | undefined): IfMatchHeaders | undefined {
  return version === undefined ? undefined : { 'If-Match': `"${version}"` }
}

/** Generated orval functions of one resource (see `api/index.ts`). */
export interface ResourceClient<T, TInput, TParams> {
  /** URL prefix of the generated query keys, used for cache invalidation. */
  keyPrefix: string
  list: (params: TParams, options?: RequestOptions) => Promise<PageData<T> | T[]>
  listKey: (params: TParams) => readonly unknown[]
  get: (id: string, options?: RequestOptions) => Promise<T>
  getKey: (id: string) => readonly unknown[]
  create: (input: TInput, options?: RequestOptions) => Promise<T>
  update: (id: string, input: TInput, headers?: IfMatchHeaders, options?: RequestOptions) => Promise<T>
  remove: (id: string, headers?: IfMatchHeaders, options?: RequestOptions) => Promise<void>
}

export interface SaveVariables<TInput> {
  /** existing record → PUT with If-Match; otherwise POST */
  id?: string
  version?: number
  input: TInput
}

const AUDIT_KEY_PREFIX = '/api/v1/audit-events'

/** Invalidates all cached queries of a resource plus audit trails (every write adds an audit entry). */
export function invalidateResource(queryClient: QueryClient, keyPrefix: string) {
  return queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey[0]
      return typeof key === 'string' && (key.startsWith(keyPrefix) || key.startsWith(AUDIT_KEY_PREFIX))
    },
  })
}

/**
 * React Query hooks over the generated client: list/page, one, save (create or If-Match update), deactivate.
 * Mutations invalidate every cached query of the resource (generated keys start with the URL).
 */
export function createResource<T extends { id: string }, TInput, TParams>(client: ResourceClient<T, TInput, TParams>) {
  return {
    keyPrefix: client.keyPrefix,
    fetchOne: (id: string) => client.get(id),
    usePage(params: TParams) {
      return useQuery({
        queryKey: client.listKey(params),
        queryFn: ({ signal }) => client.list(params, { signal }) as Promise<PageData<T>>,
        placeholderData: keepPreviousData,
      })
    },
    /** Non-paginated lists (catalogue). */
    useAll(params: TParams) {
      return useQuery({
        queryKey: client.listKey(params),
        queryFn: ({ signal }) => client.list(params, { signal }) as Promise<T[]>,
      })
    },
    useOne(id: string | undefined) {
      return useQuery({
        queryKey: client.getKey(id ?? ''),
        queryFn: ({ signal }) => client.get(id!, { signal }),
        enabled: Boolean(id),
      })
    },
    useSave() {
      const queryClient = useQueryClient()
      return useMutation({
        meta: { silent: true },
        mutationFn: ({ id, version, input }: SaveVariables<TInput>) =>
          id ? client.update(id, input, ifMatch(version)) : client.create(input),
        onSuccess: () => invalidateResource(queryClient, client.keyPrefix),
      })
    },
    /** PUT with `active: true` reactivates a deactivated record (other fields unchanged). */
    useReactivate(toRequest: (row: T) => TInput) {
      const queryClient = useQueryClient()
      return useMutation({
        mutationFn: (row: T & { version?: number }) =>
          client.update(row.id, { ...toRequest(row), active: true }, ifMatch(row.version)),
        onSuccess: () => invalidateResource(queryClient, client.keyPrefix),
      })
    },
    /** DELETE = deactivate (physical delete if never referenced). */
    useDeactivate() {
      const queryClient = useQueryClient()
      return useMutation({
        mutationFn: ({ id, version }: { id: string; version?: number }) => client.remove(id, ifMatch(version)),
        onSuccess: () => invalidateResource(queryClient, client.keyPrefix),
      })
    },
  }
}

export type Resource<T extends { id: string }, TInput, TParams> = ReturnType<typeof createResource<T, TInput, TParams>>
