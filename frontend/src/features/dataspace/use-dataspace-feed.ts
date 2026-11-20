import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { useLiveEvents } from '@/features/exchange/live/live-bus'

import { dataspaceKeys, getRecentMessages, useRecentMessages, type DataspaceMessage, type Topology } from './api'
import {
  FLOW_DURATION_MS,
  messageFromEvent,
  replaySchedule,
  REPLAY_DURATION_MS,
  toAnimation,
  type FlowAnimation,
} from './model/flows'

const RECENT_LIMIT = 50
/** Keep a finished animation briefly for the arrival ripple. */
const LINGER_MS = 900
const REFETCH_DEBOUNCE_MS = 400

export interface ReplayState {
  running: boolean
  startedAt: number
  total: number
  played: number
}

/**
 * Live data of the dataspace view: recent messages (initial), animations from live events – `connector.log`
 * animates at once, any event refetches the recent list and unseen messages animate. De-duplication by message
 * id (`recent[].id` = `messageId` of the events),
 * and the 15-minute replay. Animations are driven by one rAF clock that only runs while something moves.
 */
export function useDataspaceFeed(topology: Topology | undefined, durationMs = FLOW_DURATION_MS) {
  const queryClient = useQueryClient()
  const recent = useRecentMessages(RECENT_LIMIT)
  const [messages, setMessages] = useState<DataspaceMessage[]>([])
  const [animations, setAnimations] = useState<FlowAnimation[]>([])
  const [now, setNow] = useState(() => performance.now())
  const [replay, setReplay] = useState<ReplayState | null>(null)
  const seen = useRef(new Set<string>())
  const initialised = useRef(false)
  const timers = useRef<number[]>([])
  const refetchTimer = useRef<number | undefined>(undefined)

  const partners = useMemo(() => new Set(topology?.partners.map((p) => p.bpnl) ?? []), [topology])

  // initial list: everything already there counts as seen (no animation on page load)
  useEffect(() => {
    if (!recent.data) return
    if (!initialised.current) {
      initialised.current = true
      recent.data.forEach((m) => seen.current.add(m.id))
      setMessages(recent.data)
    }
  }, [recent.data])

  const animate = useCallback(
    (m: Parameters<typeof toAnimation>[0]) => {
      const a = toAnimation(m, performance.now(), partners, durationMs)
      if (a) setAnimations((list) => [...list, a])
    },
    [partners, durationMs],
  )

  const refetchRecent = useCallback(() => {
    window.clearTimeout(refetchTimer.current)
    refetchTimer.current = window.setTimeout(async () => {
      try {
        const fresh = await queryClient.fetchQuery({
          queryKey: dataspaceKeys.recent(RECENT_LIMIT),
          queryFn: ({ signal }) => getRecentMessages(RECENT_LIMIT, { signal }),
          staleTime: 0,
        })
        const unseen = fresh.filter((m) => !seen.current.has(m.id))
        unseen.forEach((m) => seen.current.add(m.id))
        // oldest first so a burst animates in order
        ;[...unseen].reverse().forEach((m) => animate(m))
        setMessages(fresh)
      } catch {
        // the view keeps working with what it has
      }
    }, REFETCH_DEBOUNCE_MS)
  }, [animate, queryClient])

  useLiveEvents((event) => {
    if (event.type !== 'exchange.changed' && event.type !== 'connector.log') return
    const m = messageFromEvent(event)
    if (m && !seen.current.has(m.id)) {
      seen.current.add(m.id)
      animate(m)
    }
    refetchRecent()
  })

  // one clock for all animations
  useEffect(() => {
    if (animations.length === 0) return
    let frame = 0
    const tick = () => {
      const t = performance.now()
      setNow(t)
      setAnimations((list) => {
        const alive = list.filter((a) => t < a.start + a.duration + LINGER_MS)
        return alive.length === list.length ? list : alive
      })
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [animations.length])

  const stopReplay = useCallback(() => {
    timers.current.forEach((id) => window.clearTimeout(id))
    timers.current = []
    setReplay(null)
  }, [])

  /** Time-lapse of the last 15 minutes (≈ 24 s). */
  const startReplay = useCallback(() => {
    stopReplay()
    const schedule = replaySchedule(messages, Date.now())
    const startedAt = performance.now()
    setReplay({ running: true, startedAt, total: schedule.length, played: 0 })
    schedule.forEach(({ message, offset }, index) => {
      timers.current.push(
        window.setTimeout(() => {
          animate(message)
          setReplay((r) => (r ? { ...r, played: index + 1 } : r))
        }, offset),
      )
    })
    const end = (schedule.at(-1)?.offset ?? 0) + durationMs + 200
    timers.current.push(window.setTimeout(() => setReplay(null), Math.max(end, schedule.length ? 0 : 1500)))
    return schedule.length
  }, [animate, durationMs, messages, stopReplay])

  useEffect(
    () => () => {
      timers.current.forEach((id) => window.clearTimeout(id))
      window.clearTimeout(refetchTimer.current)
    },
    [],
  )

  return {
    recent,
    messages,
    animations,
    now,
    replay,
    replayDurationMs: REPLAY_DURATION_MS,
    startReplay,
    stopReplay,
  }
}
