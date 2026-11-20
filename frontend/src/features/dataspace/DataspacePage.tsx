import { getRouteApi } from '@tanstack/react-router'
import { History, Maximize2, Minimize2, Square, TriangleAlert } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'

import { BrandMark } from '@/components/BrandMark'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAppConfig } from '@/features/config/config-context'
import { LiveIndicator } from '@/features/exchange/live/LiveIndicator'
import { useReducedMotion } from '@/hooks/use-reduced-motion'
import { cn } from '@/lib/utils'

import { useTopology, type DataspaceMessage, type Topology } from './api'
import { DataspaceGraph } from './DataspaceGraph'
import { DataspaceLegend } from './DataspaceLegend'
import { layoutTopology } from './model/layout'
import { RecentMessages } from './RecentMessages'
import { useDataspaceFeed } from './use-dataspace-feed'
import { useFlowLabels } from './use-flow-labels'

const routeApi = getRouteApi('/dataspace')

/**
 * Dataspace view (E5-06): the own connector and its partners, animated from real exchange data.
 * `?present=1` = presentation mode (full screen, larger type, no navigation) for trade-fair screens.
 */
export function DataspacePage() {
  const { t } = useTranslation('dataspace')
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const present = search.present === true
  const enteredFullscreen = useRef(false)

  const setPresent = (on: boolean) => {
    if (on && document.documentElement.requestFullscreen && !document.fullscreenElement) {
      document.documentElement
        .requestFullscreen()
        .then(() => (enteredFullscreen.current = true))
        .catch(() => undefined)
    }
    if (!on && document.fullscreenElement && document.exitFullscreen)
      void document.exitFullscreen().catch(() => undefined)
    void navigate({ search: (prev) => ({ ...prev, present: on ? true : undefined }), replace: true })
  }

  // Esc leaves presentation mode; leaving browser full screen (Esc there) too
  useEffect(() => {
    if (!present) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setPresent(false)
    const onFullscreen = () => {
      if (!document.fullscreenElement && enteredFullscreen.current) {
        enteredFullscreen.current = false
        void navigate({ search: (prev) => ({ ...prev, present: undefined }), replace: true })
      }
    }
    window.addEventListener('keydown', onKey)
    document.addEventListener('fullscreenchange', onFullscreen)
    return () => {
      window.removeEventListener('keydown', onKey)
      document.removeEventListener('fullscreenchange', onFullscreen)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- navigate is stable
  }, [present])

  const view = <DataspaceView present={present} onPresentChange={setPresent} />
  if (!present) return view
  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={t('present.label')}
      className="fixed inset-0 z-50 overflow-hidden bg-background text-[1.125rem]"
      data-testid="dataspace-present"
    >
      {view}
    </div>,
    document.body,
  )
}

function DataspaceView({ present, onPresentChange }: { present: boolean; onPresentChange: (on: boolean) => void }) {
  const { t } = useTranslation('dataspace')
  const config = useAppConfig()
  const topology = useTopology()
  const reducedMotion = useReducedMotion()
  const feed = useDataspaceFeed(topology.data, reducedMotion ? 3200 : undefined)
  const [highlight, setHighlight] = useState<string>()
  const [replayNote, setReplayNote] = useState<string>()
  const layout = useMemo(() => (topology.data ? layoutTopology(topology.data) : undefined), [topology.data])
  const lastByPartner = useMemo(() => lastMessagePerPartner(feed.messages), [feed.messages])
  const announcement = useAnnouncement(feed.animations, topology.data)
  const participant = topology.data?.self.name ?? config.participant.name

  const replay = () => {
    setReplayNote(undefined)
    const count = feed.startReplay()
    if (count === 0) setReplayNote(t('replay.empty'))
  }

  const controls = (
    <div className="flex flex-wrap items-center gap-2">
      {present ? <LiveIndicator /> : null}
      {feed.replay ? (
        <Button variant="outline" size={present ? 'lg' : 'default'} onClick={feed.stopReplay} data-action="stop-replay">
          <Square aria-hidden="true" />
          {t('replay.stop', { played: feed.replay.played, total: feed.replay.total })}
        </Button>
      ) : (
        <Button
          variant="outline"
          size={present ? 'lg' : 'default'}
          onClick={replay}
          disabled={!topology.data}
          data-action="replay"
        >
          <History aria-hidden="true" />
          {t('replay.start')}
        </Button>
      )}
      <Button
        variant={present ? 'secondary' : 'default'}
        size={present ? 'lg' : 'default'}
        onClick={() => onPresentChange(!present)}
        data-action="present"
      >
        {present ? <Minimize2 aria-hidden="true" /> : <Maximize2 aria-hidden="true" />}
        {present ? t('present.exit') : t('present.enter')}
      </Button>
    </div>
  )

  return (
    <div className={cn('flex flex-col gap-4', present ? 'h-full p-6 lg:p-8' : 'space-y-2')}>
      {present ? (
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <BrandMark className="size-10" />
            <div>
              <h1 className="text-2xl font-semibold tracking-tight lg:text-3xl">{t('title')}</h1>
              <p className="text-muted-foreground" data-testid="dataspace-caption">
                {t('caption', { participant })}
              </p>
            </div>
          </div>
          {controls}
        </header>
      ) : (
        <>
          <PageHeader title={t('title')} description={t('description')} actions={controls} />
          <p className="text-sm text-muted-foreground" data-testid="dataspace-caption">
            {t('caption', { participant })}
          </p>
        </>
      )}

      <p aria-live="polite" className="sr-only">
        {announcement}
      </p>
      {replayNote ? (
        <p role="status" className="text-sm text-muted-foreground">
          {replayNote}
        </p>
      ) : null}

      {topology.isPending ? (
        <Skeleton className="aspect-[108/70] w-full rounded-xl" />
      ) : topology.isError || !topology.data || !layout ? (
        <EmptyState
          icon={TriangleAlert}
          title={t('loadError')}
          action={
            <Button variant="outline" size="sm" onClick={() => void topology.refetch()}>
              {t('retry')}
            </Button>
          }
        />
      ) : (
        <div
          className={cn(
            'grid min-h-0 gap-4',
            present ? 'flex-1 xl:grid-cols-[minmax(0,1fr)_22rem]' : 'lg:grid-cols-[minmax(0,1fr)_19rem]',
          )}
        >
          <Card className={cn('relative min-h-0 gap-0 overflow-hidden py-0', present && 'h-full')}>
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_center,var(--participant-soft),transparent_65%)] opacity-60"
            />
            <CardContent className={cn('relative flex min-h-0 flex-col gap-3 p-3 sm:p-4', present && 'h-full')}>
              {feed.replay ? <ReplayBar played={feed.replay.played} total={feed.replay.total} /> : null}
              <div className={cn('min-h-0', present ? 'flex-1' : 'aspect-[108/70]')}>
                <DataspaceGraph
                  topology={topology.data}
                  layout={layout}
                  animations={feed.animations}
                  now={feed.now}
                  reducedMotion={reducedMotion}
                  highlight={highlight}
                  lastByPartner={lastByPartner}
                />
              </div>
              {topology.data.partners.length === 0 ? (
                <p className="text-center text-sm text-muted-foreground">{t('noPartners')}</p>
              ) : null}
              <DataspaceLegend className={cn('border-t pt-3', present ? 'text-base' : 'text-xs')} />
            </CardContent>
          </Card>
          <Card className={cn('min-h-0 gap-0 py-4', present && 'hidden xl:flex')}>
            <CardContent className={cn('flex min-h-0 flex-1 flex-col px-4', present ? 'text-base' : 'text-sm')}>
              <RecentMessages
                messages={feed.messages}
                onHighlight={setHighlight}
                limit={present ? 10 : 8}
                className="flex-1"
              />
              {reducedMotion ? <p className="mt-3 text-xs text-muted-foreground">{t('reducedMotion')}</p> : null}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function ReplayBar({ played, total }: { played: number; total: number }) {
  const { t } = useTranslation('dataspace')
  const ratio = total ? played / total : 1
  return (
    <div className="flex items-center gap-3 text-[0.75em] text-muted-foreground" data-testid="replay-bar">
      <History aria-hidden="true" className="size-4" />
      <span>{t('replay.running')}</span>
      <div
        className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-label={t('replay.progress')}
        aria-valuenow={played}
        aria-valuemin={0}
        aria-valuemax={total}
      >
        <div
          className="h-full rounded-full bg-participant transition-[width] duration-300"
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
      <span className="tabular">{t('replay.count', { played, total })}</span>
    </div>
  )
}

function lastMessagePerPartner(messages: DataspaceMessage[]): Map<string, DataspaceMessage> {
  const map = new Map<string, DataspaceMessage>()
  for (const m of [...messages].sort((a, b) => b.at.localeCompare(a.at))) {
    const bpnl = m.partner.bpnl
    if (bpnl && !map.has(bpnl)) map.set(bpnl, m)
  }
  return map
}

/** Screen reader announcement of the newest animation. */
function useAnnouncement(
  animations: ReturnType<typeof useDataspaceFeed>['animations'],
  topology: Topology | undefined,
) {
  const { t } = useTranslation('dataspace')
  const labels = useFlowLabels()
  const last = animations.at(-1)
  if (!last) return ''
  const partner = topology?.partners.find((p) => p.bpnl === last.partnerBpnl)?.name ?? last.partnerBpnl
  return t(last.direction === 'toPartner' ? 'announce.to' : 'announce.from', {
    type: labels.type(last.messageType),
    partner,
    caseNumber: last.caseNumber ?? '',
  })
}
