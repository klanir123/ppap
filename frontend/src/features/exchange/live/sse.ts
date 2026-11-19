/**
 * Minimal fetch-based Server-Sent Events client (EventSource cannot send an `Authorization` header).
 * Own implementation instead of @microsoft/fetch-event-source (2.0.1, unmaintained since 2021):
 * reconnect with exponential backoff + jitter, heartbeat watchdog, pause while the tab is hidden.
 */

export interface SseMessage {
  event: string
  data: string
  id?: string
}

/** Incremental `text/event-stream` parser (WHATWG HTML §9.2.6). */
export class SseParser {
  private buffer = ''
  private data: string[] = []
  private event = ''
  private id: string | undefined
  /** Last `retry:` value sent by the server (ms). */
  retry: number | undefined
  lastEventId: string | undefined

  feed(chunk: string): SseMessage[] {
    this.buffer += chunk
    const messages: SseMessage[] = []
    // keep a trailing '\r' in the buffer – it may be the first half of '\r\n'
    const lines = this.buffer.split(/\r\n|\r(?!$)|\n/)
    this.buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === '') {
        if (this.data.length > 0) {
          messages.push({ event: this.event || 'message', data: this.data.join('\n'), id: this.id })
        }
        this.data = []
        this.event = ''
        continue
      }
      if (line.startsWith(':')) continue // comment / keep-alive
      const colon = line.indexOf(':')
      const field = colon === -1 ? line : line.slice(0, colon)
      let value = colon === -1 ? '' : line.slice(colon + 1)
      if (value.startsWith(' ')) value = value.slice(1)
      if (field === 'data') this.data.push(value)
      else if (field === 'event') this.event = value
      else if (field === 'id' && !value.includes('\0')) {
        this.id = value
        this.lastEventId = value
      } else if (field === 'retry' && /^\d+$/.test(value)) this.retry = Number(value)
    }
    return messages
  }
}

export type LiveStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'paused' | 'closed'

export interface VisibilitySource {
  hidden: () => boolean
  subscribe: (listener: () => void) => () => void
}

const documentVisibility: VisibilitySource = {
  hidden: () => typeof document !== 'undefined' && document.visibilityState === 'hidden',
  subscribe: (listener) => {
    if (typeof document === 'undefined') return () => {}
    document.addEventListener('visibilitychange', listener)
    return () => document.removeEventListener('visibilitychange', listener)
  },
}

export interface EventStreamOptions {
  url: string
  getToken: () => Promise<string | undefined> | string | undefined
  onMessage: (message: SseMessage) => void
  onStatus?: (status: LiveStatus) => void
  /** Called after each successful (re)connect; `resumed` = events may have been missed before. */
  onOpen?: (resumed: boolean) => void
  fetchImpl?: typeof fetch
  initialDelayMs?: number
  maxDelayMs?: number
  /** No bytes for this long → connection is considered dead (server heartbeat: 25 s). */
  heartbeatTimeoutMs?: number
  /** Close the connection when the tab stays hidden this long. */
  pauseAfterHiddenMs?: number
  visibility?: VisibilitySource
  random?: () => number
}

export interface EventStream {
  start: () => void
  stop: () => void
}

export class HttpStatusError extends Error {
  readonly status: number
  constructor(status: number) {
    super(`SSE connection failed with HTTP ${status}`)
    this.status = status
  }
}

/** Backoff for attempt n (0-based): initial · 2ⁿ capped at max, with ±25 % jitter. */
export function backoffDelay(attempt: number, initial: number, max: number, random: () => number = Math.random) {
  const base = Math.min(max, initial * 2 ** attempt)
  return Math.round(base * (0.75 + random() * 0.5))
}

export function createEventStream(options: EventStreamOptions): EventStream {
  const {
    url,
    getToken,
    onMessage,
    onStatus,
    onOpen,
    fetchImpl = (...args) => globalThis.fetch(...args),
    initialDelayMs = 1_000,
    maxDelayMs = 30_000,
    heartbeatTimeoutMs = 60_000,
    pauseAfterHiddenMs = 15_000,
    visibility = documentVisibility,
    random = Math.random,
  } = options

  let status: LiveStatus = 'idle'
  let stopped = true
  let attempt = 0
  let everOpened = false
  let missedWhilePaused = false
  let controller: AbortController | undefined
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let heartbeatTimer: ReturnType<typeof setTimeout> | undefined
  let pauseTimer: ReturnType<typeof setTimeout> | undefined
  let unsubscribe: (() => void) | undefined
  let serverRetry: number | undefined
  let lastEventId: string | undefined

  const setStatus = (next: LiveStatus) => {
    if (next === status) return
    status = next
    onStatus?.(next)
  }

  const clear = (timer: ReturnType<typeof setTimeout> | undefined) => {
    if (timer !== undefined) clearTimeout(timer)
  }

  function disconnect() {
    clear(heartbeatTimer)
    clear(reconnectTimer)
    heartbeatTimer = reconnectTimer = undefined
    controller?.abort()
    controller = undefined
  }

  function scheduleReconnect() {
    if (stopped || visibility.hidden()) return
    disconnect()
    setStatus('reconnecting')
    const delay = backoffDelay(attempt, serverRetry ?? initialDelayMs, maxDelayMs, random)
    attempt += 1
    reconnectTimer = setTimeout(() => void connect(), delay)
  }

  function armHeartbeat(own: AbortController) {
    clear(heartbeatTimer)
    heartbeatTimer = setTimeout(() => {
      if (controller === own) scheduleReconnect()
    }, heartbeatTimeoutMs)
  }

  async function connect() {
    disconnect()
    if (stopped) return
    const own = new AbortController()
    controller = own
    setStatus(everOpened ? 'reconnecting' : 'connecting')
    try {
      const token = await getToken()
      if (own.signal.aborted) return
      const headers: Record<string, string> = { Accept: 'text/event-stream', 'Cache-Control': 'no-cache' }
      if (token) headers.Authorization = `Bearer ${token}`
      if (lastEventId) headers['Last-Event-ID'] = lastEventId
      const response = await fetchImpl(url, { headers, signal: own.signal, cache: 'no-store' })
      if (own.signal.aborted) return
      if (!response.ok || !response.body) throw new HttpStatusError(response.status)

      attempt = 0
      setStatus('open')
      onOpen?.(everOpened || missedWhilePaused)
      everOpened = true
      missedWhilePaused = false
      armHeartbeat(own)

      const parser = new SseParser()
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      for (;;) {
        const { done, value } = await reader.read()
        if (own.signal.aborted) return
        if (done) break
        armHeartbeat(own)
        for (const message of parser.feed(decoder.decode(value, { stream: true }))) onMessage(message)
        serverRetry = parser.retry ?? serverRetry
        lastEventId = parser.lastEventId ?? lastEventId
      }
      // server closed the stream – reconnect
      if (controller === own) scheduleReconnect()
    } catch {
      if (own.signal.aborted || controller !== own) return
      scheduleReconnect()
    }
  }

  function onVisibilityChange() {
    if (stopped) return
    if (visibility.hidden()) {
      clear(pauseTimer)
      pauseTimer = setTimeout(() => {
        if (stopped || !visibility.hidden()) return
        disconnect()
        missedWhilePaused = true
        setStatus('paused')
      }, pauseAfterHiddenMs)
      return
    }
    clear(pauseTimer)
    pauseTimer = undefined
    if (status === 'paused' || status === 'reconnecting') {
      attempt = 0
      void connect()
    }
  }

  return {
    start() {
      if (!stopped) return
      stopped = false
      unsubscribe = visibility.subscribe(onVisibilityChange)
      if (visibility.hidden()) {
        missedWhilePaused = false
        setStatus('paused')
      } else void connect()
    },
    stop() {
      stopped = true
      clear(pauseTimer)
      disconnect()
      unsubscribe?.()
      unsubscribe = undefined
      setStatus('closed')
    },
  }
}
