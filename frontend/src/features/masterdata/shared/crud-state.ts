import { useCallback, useState } from 'react'

/** UI state of a CRUD list page: drawer (new / edit), deactivate confirmation, import wizard. */
export function useCrudState<T>() {
  const [editing, setEditing] = useState<T | 'new' | null>(null)
  const [deactivating, setDeactivating] = useState<T | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const openNew = useCallback(() => setEditing('new'), [])
  const openEdit = useCallback((row: T) => setEditing(row), [])
  const closeEditor = useCallback(() => setEditing(null), [])
  return {
    editing,
    isNew: editing === 'new',
    current: editing && editing !== 'new' ? editing : undefined,
    openNew,
    openEdit,
    closeEditor,
    setEditing,
    deactivating,
    setDeactivating,
    importOpen,
    setImportOpen,
  }
}
