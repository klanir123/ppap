import { Link } from '@tanstack/react-router'
import { Activity, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { STATUS_ICONS } from '@/components/status-icons'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useActivity, type ActivityEntry, type CaseStatus } from '@/features/approvals/api'
import { useFormat, useRelativeTime } from '@/features/approvals/components/format'
import { ActorText } from '@/features/approvals/components/Timeline'
import { caseStatusToken } from '@/features/approvals/model/status'
import { useLiveConnected } from '@/features/exchange/live/live-context'
import { statusStyle } from '@/lib/status-style'

const LIMIT = 30
/** Without a live connection the feed refreshes itself. */
const FALLBACK_POLL_MS = 30_000

/** Activity feed (UC-11): latest timeline entries of all cases, actor as user / partner name, live via SSE. */
export function ActivityFeed({ className }: { className?: string }) {
  const { t } = useTranslation(['dashboard', 'approvals', 'status'])
  const live = useLiveConnected()
  const query = useActivity(LIMIT, { refetchInterval: live ? false : FALLBACK_POLL_MS })
  const relative = useRelativeTime()
  const fmt = useFormat()
  const entries = query.data ?? []

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Activity aria-hidden="true" className="size-4 text-muted-foreground" />
          {t('activity.title')}
        </CardTitle>
        <CardDescription>{t('activity.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <div className="space-y-3" aria-busy="true">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : query.isError ? (
          <p className="text-sm text-muted-foreground">{t('activity.error')}</p>
        ) : entries.length === 0 ? (
          <p className="rounded-md border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
            {t('activity.empty')}
          </p>
        ) : (
          <ol
            className="max-h-[26rem] space-y-1 overflow-y-auto pr-1"
            aria-label={t('activity.title')}
            data-testid="activity-feed"
          >
            {entries.map((entry, index) => (
              <li key={`${entry.caseId}-${entry.at}-${index}`}>
                <ActivityItem entry={entry} relative={relative(entry.at)} absolute={fmt.dateTime(entry.at)} />
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}

function ActivityItem({ entry, relative, absolute }: { entry: ActivityEntry; relative: string; absolute: string }) {
  const { t } = useTranslation(['dashboard', 'approvals', 'status'])
  const token = entry.toStatus ? caseStatusToken(entry.toStatus as CaseStatus) : 'draft'
  const Icon = STATUS_ICONS.approval[token] as LucideIcon
  const label = t(`approvals:timeline.actions.${entry.action}` as 'approvals:timeline.actions.SEND', {
    defaultValue: entry.toStatus ? t(`status:approval.${token}`) : entry.action,
  })
  return (
    <Link
      to="/approvals/$caseId"
      params={{ caseId: entry.caseId }}
      className="-mx-1 flex gap-3 rounded-md px-1 py-2 hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring"
      style={statusStyle(token)}
    >
      <span
        aria-hidden="true"
        className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full border border-(--sb-border) bg-(--sb-bg) text-(--sb-fg)"
      >
        <Icon className="size-3.5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-baseline gap-x-2 text-sm">
          <span className="font-medium">{label}</span>
          <span className="font-mono text-xs text-muted-foreground">{entry.caseNumber}</span>
        </span>
        <span className="block truncate text-xs text-muted-foreground">
          <ActorText actor={entry.actor} actorDisplay={entry.actorDisplay} />
          {entry.partner ? (
            <>
              <span aria-hidden="true"> · </span>
              {t('activity.partner', { partner: entry.partner.name ?? entry.partner.bpnl })}
            </>
          ) : null}
        </span>
      </span>
      <time dateTime={entry.at} title={absolute} className="shrink-0 pt-0.5 text-[11px] text-muted-foreground tabular">
        {relative}
      </time>
    </Link>
  )
}
