import type { ConnectorLogEvent, LogLevel } from '../api/types'

export const LOG_BUFFER_SIZE = 500

export interface LogEntry extends ConnectorLogEvent {
  /** Stable client key. */
  key: string
}

export type LevelFilter = 'ALL' | 'INFO' | 'WARN' | 'ERROR'
export const LEVEL_FILTERS = ['ALL', 'INFO', 'WARN', 'ERROR'] as const satisfies readonly LevelFilter[]

const RANK: Record<LogLevel, number> = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 }

export function levelRank(level: string): number {
  return RANK[level.toUpperCase() as LogLevel] ?? 1
}

/** Newest first, capped. */
export function appendLog(buffer: readonly LogEntry[], entry: LogEntry, max = LOG_BUFFER_SIZE): LogEntry[] {
  return [entry, ...buffer].slice(0, max)
}

/** Level = minimum level; text matches text, message type, step and correlation id (case-insensitive). */
export function filterLog(entries: readonly LogEntry[], level: LevelFilter, text: string): LogEntry[] {
  const needle = text.trim().toLowerCase()
  const min = level === 'ALL' ? -1 : levelRank(level)
  return entries.filter((e) => {
    if (levelRank(e.level) < min) return false
    if (!needle) return true
    return [e.text, e.messageType, e.step, e.correlationId].some((v) => v?.toLowerCase().includes(needle))
  })
}

let counter = 0
export function toLogEntry(event: ConnectorLogEvent): LogEntry {
  counter += 1
  return { ...event, key: event.id ?? `${event.at}-${counter}` }
}

export function levelToken(level: string): string {
  switch (level.toUpperCase()) {
    case 'ERROR':
      return 'error'
    case 'WARN':
      return 'warning'
    case 'DEBUG':
      return 'draft'
    default:
      return 'in-progress'
  }
}
