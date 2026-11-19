import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'

import { isApiError } from '@/api/problem'

import {
  approvalKeys,
  approvalsApi,
  invalidateApprovals,
  type DraftElementInput,
  type Submission,
  type Versioned,
} from '../api'
import { problemCode } from '../components/problem'
import { normalizePointer, type SchemaError } from '../schema-form/schema'

export type SaveState = 'idle' | 'saving' | 'saved' | 'error' | 'conflict' | 'invalid'

export interface ElementSaveStatus {
  state: SaveState
  /** VALUE_SCHEMA_VIOLATION errors at JSON pointers. */
  valueErrors?: SchemaError[]
  message?: string
}

/** Problem `errors[]` → schema errors at JSON pointers (field = pointer into the values). */
export function valueErrorsOf(error: unknown): SchemaError[] {
  if (!isApiError(error)) return []
  const list = error.problem?.errors ?? []
  const mapped = list.map((e) => ({
    pointer: normalizePointer(e.field?.replace(/^values(?=[/.[]|$)/, '')),
    code: 'VALUE_SCHEMA_VIOLATION' as const,
    message: e.message,
  }))
  return mapped.length ? mapped : [{ pointer: '', code: 'VALUE_SCHEMA_VIOLATION', message: error.problem?.detail }]
}

/**
 * Serialised writes on the draft revision with optimistic locking: every write carries
 * `If-Match: <case version>` and takes the new version from the ETag (or reloads the case).
 */
export function useDraftSaver(caseId: string, initialVersion: number) {
  const queryClient = useQueryClient()
  const versionRef = useRef(initialVersion)
  const chain = useRef<Promise<unknown>>(Promise.resolve())
  const pendingCount = useRef(0)
  const [status, setStatus] = useState<Record<string, ElementSaveStatus>>({})
  const [busy, setBusy] = useState(false)
  /** Incremented when editors must re-read the server state (conflict reload, Excel import). */
  const [epoch, setEpoch] = useState(0)

  const setFor = (code: string, next: ElementSaveStatus) => setStatus((prev) => ({ ...prev, [code]: next }))

  const refreshVersion = useCallback(async () => {
    const fresh = await approvalsApi.getCase(caseId)
    versionRef.current = fresh.version
    queryClient.setQueryData(approvalKeys.detail(caseId), fresh)
    return fresh.version
  }, [caseId, queryClient])

  /** Runs a versioned write in the queue; updates version + cached draft. */
  const enqueue = useCallback(
    <T>(code: string, write: (version: number) => Promise<Versioned<T>>): Promise<T> => {
      pendingCount.current += 1
      setBusy(true)
      setFor(code, { state: 'saving' })
      const run = chain.current.then(async () => {
        try {
          const result = await write(versionRef.current)
          if (result.version !== undefined) versionRef.current = result.version
          else await refreshVersion()
          if (result.data && typeof result.data === 'object' && 'elements' in (result.data as object)) {
            queryClient.setQueryData(approvalKeys.submission(caseId, 'draft'), result.data as unknown as Submission)
          } else {
            await queryClient.invalidateQueries({ queryKey: approvalKeys.submission(caseId, 'draft') })
          }
          setFor(code, { state: 'saved' })
          return result.data
        } catch (error) {
          const errorCode = problemCode(error)
          if (errorCode === 'VERSION_CONFLICT') setFor(code, { state: 'conflict' })
          else if (errorCode === 'VALUE_SCHEMA_VIOLATION' || (isApiError(error) && error.status === 422))
            setFor(code, { state: 'invalid', valueErrors: valueErrorsOf(error) })
          else setFor(code, { state: 'error', message: errorCode })
          throw error
        } finally {
          pendingCount.current -= 1
          if (pendingCount.current === 0) setBusy(false)
        }
      })
      chain.current = run.catch(() => undefined)
      return run
    },
    [caseId, queryClient, refreshVersion],
  )

  const saveElement = useCallback(
    (code: string, input: DraftElementInput) =>
      enqueue(code, (version) => approvalsApi.putDraftElement(caseId, code, input, version)),
    [caseId, enqueue],
  )
  const addDocument = useCallback(
    (code: string, documentId: string) =>
      enqueue(code, (version) => approvalsApi.addDraftDocument(caseId, code, documentId, version)),
    [caseId, enqueue],
  )
  const removeDocument = useCallback(
    (code: string, documentId: string) =>
      enqueue(code, (version) => approvalsApi.removeDraftDocument(caseId, code, documentId, version)),
    [caseId, enqueue],
  )

  /** After a conflict: reload the server state (discarding local edits of the element). */
  const reload = useCallback(
    async (code: string) => {
      await refreshVersion()
      await queryClient.invalidateQueries({ queryKey: approvalKeys.submission(caseId, 'draft') })
      setFor(code, { state: 'idle' })
      setEpoch((e) => e + 1)
    },
    [caseId, queryClient, refreshVersion],
  )

  /** After a conflict: take the latest version and write the local state again. */
  const overwrite = useCallback(
    async (code: string, input: DraftElementInput) => {
      await refreshVersion()
      return saveElement(code, input)
    },
    [refreshVersion, saveElement],
  )

  const invalidate = useCallback(() => invalidateApprovals(queryClient), [queryClient])

  /** Refetch everything and remount editors with the server state. */
  const resetEditors = useCallback(async () => {
    await invalidateApprovals(queryClient)
    setEpoch((e) => e + 1)
  }, [queryClient])

  // warn before leaving with writes in flight
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (pendingCount.current > 0) e.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [])

  return {
    status,
    busy,
    epoch,
    resetEditors,
    version: () => versionRef.current,
    setVersion: (v: number) => (versionRef.current = v),
    saveElement,
    addDocument,
    removeDocument,
    reload,
    overwrite,
    refreshVersion,
    invalidate,
    /** Resolves when all queued writes are done. */
    flush: () => chain.current,
  }
}

export type DraftSaver = ReturnType<typeof useDraftSaver>
