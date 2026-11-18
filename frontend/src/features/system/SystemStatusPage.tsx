import { CircleHelp, RefreshCw, ServerCrash } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import type { ComponentStatus } from '@/api/generated/model'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { aggregateHealth, isHealthStatus } from '@/lib/status'
import { cn } from '@/lib/utils'

import { useComponentInfo } from './component-meta'
import { useSystemStatus } from './useSystemStatus'

export function SystemStatusPage() {
  const { t, i18n } = useTranslation(['system', 'common'])
  const query = useSystemStatus()
  const components = query.data?.components ?? []
  const overall = aggregateHealth(components.map((c) => c.status))
  const lastChecked = query.dataUpdatedAt
    ? new Intl.DateTimeFormat(i18n.resolvedLanguage, { timeStyle: 'medium' }).format(query.dataUpdatedAt)
    : undefined

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('title')}
        description={t('description')}
        actions={
          <>
            {lastChecked ? (
              <span className="text-xs text-muted-foreground tabular" aria-live="polite">
                {t('lastChecked', { time: lastChecked })}
              </span>
            ) : null}
            <Button variant="outline" size="sm" onClick={() => void query.refetch()} disabled={query.isFetching}>
              <RefreshCw aria-hidden="true" className={cn(query.isFetching && 'animate-spin')} />
              {t('refresh')}
            </Button>
          </>
        }
      />

      {query.isPending ? (
        <StatusSkeleton />
      ) : query.isError ? (
        <EmptyState
          icon={ServerCrash}
          title={t('error.title')}
          description={t('error.description')}
          action={
            <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
              <RefreshCw aria-hidden="true" />
              {t('common:retry')}
            </Button>
          }
        />
      ) : components.length === 0 ? (
        <EmptyState icon={CircleHelp} title={t('empty')} />
      ) : (
        <>
          <div
            className="flex items-center gap-3 rounded-lg border border-(--sb-border) bg-(--sb-bg) px-4 py-3 text-(--sb-fg)"
            style={
              {
                '--sb-bg': `var(--status-${overall.toLowerCase()}-bg)`,
                '--sb-fg': `var(--status-${overall.toLowerCase()}-fg)`,
                '--sb-border': `var(--status-${overall.toLowerCase()}-border)`,
              } as React.CSSProperties
            }
          >
            <span className="relative flex size-2.5">
              {overall === 'UP' ? (
                <span className="absolute inline-flex size-full animate-ping rounded-full bg-(--status-up-solid) opacity-60" />
              ) : null}
              <span
                className="relative inline-flex size-2.5 rounded-full"
                style={{ background: `var(--status-${overall.toLowerCase()}-solid)` }}
              />
            </span>
            <p className="text-sm font-medium">{t(`overall.${overall}`)}</p>
          </div>
          <ul className="grid gap-4 md:grid-cols-2">
            {components.map((component) => (
              <li key={component.name}>
                <ComponentCard component={component} />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function ComponentCard({ component }: { component: ComponentStatus }) {
  const info = useComponentInfo()(component.name)
  const Icon = info.icon
  const status = isHealthStatus(component.status) ? component.status : 'UNKNOWN'
  const details = Object.entries(component.details ?? {})

  return (
    <Card className="h-full">
      <CardContent className="flex h-full flex-col gap-4">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-lg border bg-muted/50 text-muted-foreground">
            <Icon aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-sm font-semibold">{info.name}</h2>
            {info.description ? <p className="mt-0.5 text-xs text-muted-foreground">{info.description}</p> : null}
          </div>
          <StatusBadge kind="health" status={status} />
        </div>
        {details.length > 0 ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-md bg-muted/40 px-3 py-2 text-xs">
            {details.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-muted-foreground">{key}</dt>
                <dd className="truncate font-mono">
                  {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                </dd>
              </div>
            ))}
          </dl>
        ) : null}
      </CardContent>
    </Card>
  )
}

function StatusSkeleton() {
  const { t } = useTranslation()
  return (
    <div className="space-y-4" aria-busy="true" aria-label={t('loading')}>
      <Skeleton className="h-12 w-full" />
      <div className="grid gap-4 md:grid-cols-2">
        {Array.from({ length: 4 }, (_, i) => (
          <Card key={i}>
            <CardContent className="flex items-start gap-3">
              <Skeleton className="size-10 rounded-lg" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-3 w-56" />
              </div>
              <Skeleton className="h-6 w-20 rounded-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  )
}
