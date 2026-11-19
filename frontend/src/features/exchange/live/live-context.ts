import { createContext, useContext } from 'react'

import type { LiveStatus } from './sse'

export const LiveStatusContext = createContext<LiveStatus>('idle')

export function useLiveStatus(): LiveStatus {
  return useContext(LiveStatusContext)
}

/** Live updates arrive – components can skip fallback polling. */
export function useLiveConnected(): boolean {
  return useContext(LiveStatusContext) === 'open'
}
