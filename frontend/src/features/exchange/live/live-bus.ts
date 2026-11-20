import { createContext, useContext, useEffect, useEffectEvent } from 'react'

import type { LiveEvent } from '../api/types'

export type LiveListener = (event: LiveEvent) => void

/** Fan-out of parsed live events to components (live log, dataspace view). */
export interface LiveBus {
  subscribe: (listener: LiveListener) => () => void
}

export function createLiveBus(): LiveBus & { publish: (event: LiveEvent) => void } {
  const listeners = new Set<LiveListener>()
  return {
    subscribe: (listener) => {
      listeners.add(listener)
      return () => void listeners.delete(listener)
    },
    publish: (event) => {
      for (const listener of listeners) {
        try {
          listener(event)
        } catch {
          // one broken consumer must not stop the others
        }
      }
    },
  }
}

const NOOP_BUS: LiveBus = { subscribe: () => () => undefined }

export const LiveBusContext = createContext<LiveBus>(NOOP_BUS)

/** Calls `listener` for every live event while mounted (latest closure, no resubscribe). */
export function useLiveEvents(listener: LiveListener) {
  const bus = useContext(LiveBusContext)
  const onEvent = useEffectEvent(listener)
  useEffect(() => bus.subscribe((event) => onEvent(event)), [bus])
}
