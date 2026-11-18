import { createContext, useContext } from 'react'

/** Read-only state of the surrounding form (missing permission). */
export const FormReadOnlyContext = createContext(false)

export function useFormReadOnly() {
  return useContext(FormReadOnlyContext)
}
