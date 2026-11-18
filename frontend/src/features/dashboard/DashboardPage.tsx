import { Link } from '@tanstack/react-router'
import { ArrowRight, CheckCircle2, Clock, Eye, FileCheck2, Inbox, Plus, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useCurrentUser, userDisplayName } from '@/features/auth/current-user'
import { useComponentInfo } from '@/features/system/component-meta'
import { useSystemStatus } from '@/features/system/useSystemStatus'
import { isHealthStatus } from '@/lib/status'

type KpiKey = 'openRequests' | 'inReview' | 'approved' | 'overdue'

const KPIS: { key: KpiKey; icon: LucideIcon }[] = [
  { key: 'openRequests', icon: Inbox },
  { key: 'inReview', icon: Eye },
  { key: 'approved', icon: CheckCircle2 },
  { key: 'overdue', icon: Clock },
]

export function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'common'])
  const me = useCurrentUser()

  return (
    <div className="space-y-8">
      <PageHeader title={t('greeting', { name: userDisplayName(me) })} description={t('description')} />

      <section aria-label={t('title')} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {KPIS.map(({ key, icon }) => (
          // Values arrive with the approval module (phase 3) – never show fake numbers.
          <KpiCard key={key} label={t(`kpi.${key}`)} icon={icon} value={undefined} />
        ))}
      </section>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>{t('recent.title')}</CardTitle>
            <CardDescription>{t('recent.description')}</CardDescription>
          </CardHeader>
          <CardContent>
            <EmptyState
              icon={FileCheck2}
              title={t('empty.title')}
              description={t('empty.description')}
              action={
                <Tooltip>
                  <TooltipTrigger asChild>
                    {/* aria-disabled instead of disabled: stays focusable so the tooltip is reachable by keyboard */}
                    <Button
                      aria-disabled="true"
                      className="cursor-not-allowed opacity-50"
                      onClick={(e) => e.preventDefault()}
                    >
                      <Plus aria-hidden="true" />
                      {t('empty.cta')}
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>{t('common:availableInPhase', { phase: 3 })}</TooltipContent>
                </Tooltip>
              }
            />
          </CardContent>
        </Card>
        <SystemHealthCard />
      </div>
    </div>
  )
}

function KpiCard({
  label,
  icon: Icon,
  value,
  loading,
}: {
  label: string
  icon: LucideIcon
  value?: number
  loading?: boolean
}) {
  const { t } = useTranslation(['dashboard', 'common'])
  return (
    <Card className="gap-3 py-5">
      <CardHeader className="px-5">
        <CardDescription className="text-xs font-medium">{label}</CardDescription>
        <CardAction>
          <Icon aria-hidden="true" className="size-4 text-muted-foreground" />
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-1 px-5">
        {loading ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <p className="text-3xl font-semibold tracking-tight tabular">{value ?? t('common:notAvailable')}</p>
        )}
        <p className="text-xs text-muted-foreground">{value === undefined && !loading ? t('kpi.noData') : ' '}</p>
      </CardContent>
    </Card>
  )
}

function SystemHealthCard() {
  const { t } = useTranslation(['dashboard', 'system'])
  const query = useSystemStatus()
  const componentInfo = useComponentInfo()

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('systemCard.title')}</CardTitle>
        <CardDescription>{t('systemCard.description')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-4">
        <ul className="divide-y rounded-md border">
          {query.isPending
            ? Array.from({ length: 4 }, (_, i) => (
                <li key={i} className="flex items-center justify-between gap-3 px-3 py-2.5">
                  <Skeleton className="h-4 w-32" />
                  <Skeleton className="h-5 w-20 rounded-full" />
                </li>
              ))
            : (query.data?.components ?? []).map((c) => {
                const info = componentInfo(c.name)
                const Icon = info.icon
                return (
                  <li key={c.name} className="flex items-center justify-between gap-3 px-3 py-2.5">
                    <span className="flex min-w-0 items-center gap-2 text-sm">
                      <Icon aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
                      <span className="truncate">{info.short}</span>
                    </span>
                    <StatusBadge kind="health" size="sm" status={isHealthStatus(c.status) ? c.status : 'UNKNOWN'} />
                  </li>
                )
              })}
          {query.isError ? (
            <li className="px-3 py-2.5 text-sm text-muted-foreground">{t('system:error.title')}</li>
          ) : null}
        </ul>
        <Button variant="outline" size="sm" className="mt-auto self-start" asChild>
          <Link to="/system">
            {t('systemCard.link')}
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </Link>
        </Button>
      </CardContent>
    </Card>
  )
}
