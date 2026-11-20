import { Pause, Play, Radio, Trash2 } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { CopyButton } from '@/components/CopyButton'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { useLiveEvents } from '../live/live-bus'
import { useLiveStatus } from '../live/live-context'
import { useMessageTypeLabel } from '../components/use-labels'
import {
  appendLog,
  filterLog,
  LEVEL_FILTERS,
  levelToken,
  toLogEntry,
  type LevelFilter,
  type LogEntry,
} from './live-log'

/**
 * Cockpit › Live-Log: SSE `connector.log` events (derived from the exchange steps) – pause / resume,
 * filter by level and text, copy the correlation id.
 */
export function LiveLogTab({ initial = [] }: { initial?: LogEntry[] }) {
  const { t } = useTranslation('exchange')
  const status = useLiveStatus()
  const textId = useId()
  const [entries, setEntries] = useState<LogEntry[]>(initial)
  const [paused, setPaused] = useState(false)
  const [queued, setQueued] = useState<LogEntry[]>([])
  const [level, setLevel] = useState<LevelFilter>('ALL')
  const [text, setText] = useState('')

  // useLiveEvents always calls the latest closure → `paused` is current
  useLiveEvents((event) => {
    if (event.type !== 'connector.log') return
    const entry = toLogEntry(event)
    if (paused) setQueued((q) => appendLog(q, entry))
    else setEntries((list) => appendLog(list, entry))
  })

  const resume = () => {
    setEntries((list) => queued.reduceRight((acc, e) => appendLog(acc, e), list))
    setQueued([])
    setPaused(false)
  }
  const visible = filterLog(entries, level, text)

  return (
    <section className="space-y-3" aria-labelledby="cockpit-log">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="cockpit-log" className="flex items-center gap-2 text-base font-semibold tracking-tight">
          <Radio
            aria-hidden="true"
            className={cn(
              'size-4',
              status === 'open' && !paused ? 'text-(--status-ok-solid)' : 'text-muted-foreground',
            )}
          />
          {t('cockpit.log.title')}
        </h2>
        <p className="max-w-2xl text-xs text-muted-foreground">{t('cockpit.log.description')}</p>
      </div>

      <div className="flex flex-col gap-2 rounded-lg border bg-card p-2 sm:flex-row sm:items-center">
        <Input
          id={textId}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={t('cockpit.log.searchPlaceholder')}
          aria-label={t('cockpit.log.search')}
          className="h-8 sm:max-w-xs"
        />
        <Select value={level} onValueChange={(v) => setLevel(v as LevelFilter)}>
          <SelectTrigger size="sm" className="w-full sm:w-44" aria-label={t('cockpit.log.level')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {LEVEL_FILTERS.map((l) => (
              <SelectItem key={l} value={l}>
                {t(`cockpit.log.levels.${l}`)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="flex items-center gap-2 sm:ml-auto">
          <span className="text-xs text-muted-foreground tabular" aria-live="polite">
            {paused && queued.length > 0
              ? t('cockpit.log.queued', { count: queued.length })
              : t('cockpit.log.count', { count: visible.length })}
          </span>
          {paused ? (
            <Button size="sm" onClick={resume} data-action="resume-log">
              <Play aria-hidden="true" />
              {t('cockpit.log.resume')}
            </Button>
          ) : (
            <Button size="sm" variant="outline" onClick={() => setPaused(true)} data-action="pause-log">
              <Pause aria-hidden="true" />
              {t('cockpit.log.pause')}
            </Button>
          )}
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setEntries([])
              setQueued([])
            }}
            disabled={entries.length === 0 && queued.length === 0}
          >
            <Trash2 aria-hidden="true" />
            {t('cockpit.log.clear')}
          </Button>
        </div>
      </div>

      {visible.length === 0 ? (
        <EmptyState
          icon={Radio}
          title={entries.length === 0 ? t('cockpit.log.emptyTitle') : t('cockpit.log.noMatch')}
          description={
            entries.length === 0
              ? status === 'open'
                ? t('cockpit.log.emptyDescription')
                : t('cockpit.log.offline')
              : undefined
          }
        />
      ) : (
        <ol
          className="max-h-[32rem] divide-y overflow-y-auto rounded-lg border bg-card font-mono text-xs"
          aria-label={t('cockpit.log.title')}
          data-testid="live-log"
        >
          {visible.map((entry) => (
            <LogRow key={entry.key} entry={entry} />
          ))}
        </ol>
      )}
    </section>
  )
}

function LogRow({ entry }: { entry: LogEntry }) {
  const { t, i18n } = useTranslation('exchange')
  const typeLabel = useMessageTypeLabel()
  const time = new Intl.DateTimeFormat(i18n.resolvedLanguage, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(entry.at))
  const level = entry.level.toUpperCase()
  return (
    <li
      className="grid grid-cols-[auto_auto_minmax(0,1fr)] items-start gap-x-3 gap-y-1 px-3 py-2 sm:grid-cols-[5.5rem_4.5rem_minmax(0,1fr)_auto]"
      data-level={level}
    >
      <time dateTime={entry.at} className="text-muted-foreground tabular">
        {time}
      </time>
      <span
        style={statusStyle(levelToken(level))}
        className="inline-flex h-5 w-fit items-center rounded border border-(--sb-border) bg-(--sb-bg) px-1.5 text-[10px] font-semibold text-(--sb-fg)"
      >
        {level}
      </span>
      <span className="min-w-0 font-sans">
        <span className="block break-words">{entry.text}</span>
        {entry.messageType || entry.step ? (
          <span className="mt-0.5 block text-[11px] text-muted-foreground">
            {[
              entry.messageType ? typeLabel(entry.messageType) : undefined,
              entry.step ? t(`steps.${entry.step}` as 'steps.CATALOG', { defaultValue: entry.step }) : undefined,
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        ) : null}
      </span>
      {entry.correlationId ? (
        <span className="col-span-3 inline-flex min-w-0 items-center gap-1 justify-self-start text-[11px] text-muted-foreground sm:col-span-1 sm:justify-self-end">
          <span className="truncate" title={entry.correlationId}>
            {entry.correlationId}
          </span>
          <CopyButton value={entry.correlationId} label={t('cockpit.log.copyCorrelation')} />
        </span>
      ) : null}
    </li>
  )
}
