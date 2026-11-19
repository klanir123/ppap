import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { backoffDelay, createEventStream, SseParser, type LiveStatus, type VisibilitySource } from './sse'

describe('SseParser', () => {
  it('parses events across chunk boundaries, multi-line data, comments, ids and retry', () => {
    const p = new SseParser()
    expect(p.feed(': keep-alive\n\nevent: case.changed\ndata: {"caseId"')).toEqual([])
    expect(p.feed(':"c-1"}\nid: 7\n\n')).toEqual([{ event: 'case.changed', data: '{"caseId":"c-1"}', id: '7' }])
    expect(p.feed('data: a\r\ndata: b\r\nretry: 5000\r\n\r\n')).toEqual([{ event: 'message', data: 'a\nb', id: '7' }])
    expect(p.retry).toBe(5000)
    expect(p.lastEventId).toBe('7')
  })

  it('ignores events without data', () => {
    expect(new SseParser().feed('event: heartbeat\n\n')).toEqual([])
  })
})

describe('backoffDelay', () => {
  it('doubles up to the maximum with ±25 % jitter', () => {
    const mid = () => 0.5
    expect([0, 1, 2, 3, 10].map((n) => backoffDelay(n, 1000, 30_000, mid))).toEqual([1000, 2000, 4000, 8000, 30_000])
    expect(backoffDelay(0, 1000, 30_000, () => 0)).toBe(750)
    expect(backoffDelay(0, 1000, 30_000, () => 1)).toBe(1250)
  })
})

/** Fake fetch: each call returns a stream the test can push into / close. */
function fakeServer() {
  const connections: { init: RequestInit; push: (s: string) => void; close: () => void }[] = []
  let failNext = 0
  const fetchImpl = vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
    if (failNext > 0) {
      failNext -= 1
      return new Response('nope', { status: 503 })
    }
    let controller!: ReadableStreamDefaultController<Uint8Array>
    const body = new ReadableStream<Uint8Array>({ start: (c) => void (controller = c) })
    init?.signal?.addEventListener('abort', () => {
      try {
        controller.error(new DOMException('aborted', 'AbortError'))
      } catch {
        /* already closed */
      }
    })
    const encoder = new TextEncoder()
    connections.push({
      init: init!,
      push: (s) => controller.enqueue(encoder.encode(s)),
      close: () => controller.close(),
    })
    return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
  })
  return { fetchImpl, connections, failTimes: (n: number) => void (failNext = n) }
}

function fakeVisibility() {
  let hidden = false
  const listeners = new Set<() => void>()
  const source: VisibilitySource = {
    hidden: () => hidden,
    subscribe: (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
  }
  return {
    source,
    set: (h: boolean) => {
      hidden = h
      listeners.forEach((l) => l())
    },
  }
}

const flush = async () => {
  for (let i = 0; i < 10; i++) await Promise.resolve()
}

describe('createEventStream', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  function setup(overrides: { token?: string } = {}) {
    const server = fakeServer()
    const visibility = fakeVisibility()
    const statuses: LiveStatus[] = []
    const messages: string[] = []
    const opens: boolean[] = []
    const stream = createEventStream({
      url: '/api/v1/events',
      getToken: async () => overrides.token ?? 'tok-1',
      fetchImpl: server.fetchImpl as unknown as typeof fetch,
      onMessage: (m) => messages.push(`${m.event}:${m.data}`),
      onStatus: (s) => statuses.push(s),
      onOpen: (resumed) => opens.push(resumed),
      visibility: visibility.source,
      random: () => 0.5,
      heartbeatTimeoutMs: 60_000,
      pauseAfterHiddenMs: 1_000,
    })
    return { server, visibility, statuses, messages, opens, stream }
  }

  it('sends the bearer token and SSE headers and delivers events', async () => {
    const { server, stream, messages, statuses } = setup()
    stream.start()
    await flush()
    const headers = server.connections[0]!.init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer tok-1')
    expect(headers.Accept).toBe('text/event-stream')
    server.connections[0]!.push('event: case.changed\ndata: {"caseId":"c-1"}\n\n')
    await flush()
    expect(messages).toEqual(['case.changed:{"caseId":"c-1"}'])
    expect(statuses).toEqual(['connecting', 'open'])
    stream.stop()
  })

  it('reconnects with exponential backoff after errors and reports a resumed open', async () => {
    const { server, stream, statuses, opens } = setup()
    server.failTimes(2)
    stream.start()
    await flush()
    expect(statuses.at(-1)).toBe('reconnecting')
    expect(server.fetchImpl).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(999)
    expect(server.fetchImpl).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(server.fetchImpl).toHaveBeenCalledTimes(2) // after 1 s
    await vi.advanceTimersByTimeAsync(2000) // second delay 2 s
    expect(server.fetchImpl).toHaveBeenCalledTimes(3)
    expect(statuses.at(-1)).toBe('open')
    expect(opens).toEqual([false])

    // server closes the stream → reconnect (attempt counter was reset)
    server.connections[0]!.close()
    await flush()
    expect(statuses.at(-1)).toBe('reconnecting')
    await vi.advanceTimersByTimeAsync(1000)
    expect(statuses.at(-1)).toBe('open')
    expect(opens).toEqual([false, true])
    stream.stop()
  })

  it('reconnects when no heartbeat arrives', async () => {
    const { server, stream } = setup()
    stream.start()
    await flush()
    await vi.advanceTimersByTimeAsync(60_000)
    await vi.advanceTimersByTimeAsync(1000)
    expect(server.fetchImpl).toHaveBeenCalledTimes(2)
    stream.stop()
  })

  it('pauses while the tab is hidden and resumes when visible', async () => {
    const { server, stream, statuses, visibility, opens } = setup()
    stream.start()
    await flush()
    visibility.set(true)
    await vi.advanceTimersByTimeAsync(1000)
    expect(statuses.at(-1)).toBe('paused')
    visibility.set(false)
    await flush()
    expect(server.fetchImpl).toHaveBeenCalledTimes(2)
    expect(statuses.at(-1)).toBe('open')
    expect(opens).toEqual([false, true])
    stream.stop()
  })

  it('stops for good (logout): aborts and never reconnects', async () => {
    const { server, stream, statuses } = setup()
    stream.start()
    await flush()
    stream.stop()
    await vi.advanceTimersByTimeAsync(120_000)
    expect(server.fetchImpl).toHaveBeenCalledTimes(1)
    expect(statuses.at(-1)).toBe('closed')
    expect((server.connections[0]!.init.signal as AbortSignal).aborted).toBe(true)
  })
})
