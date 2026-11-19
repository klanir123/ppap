import { Link } from '@tanstack/react-router'
import { AlarmClock, ArrowRight, Eye, FileCheck2, Gauge, Inbox, Plus, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useCasePage, useCaseStats } from '@/features/approvals/api'
import { CaseStatusBadge, DueDate, RoleBadge } from '@/features/approvals/components/badges'
import { localized, useFormat } from '@/features/approvals/components/format'
import { useCurrentUser, usePermission, userDisplayName } from '@/features/auth/current-user'
import { useComponentInfo } from '@/features/system/component-meta'
import { useSystemStatus } from '@/features/system/useSystemStatus'
import { isHealthStatus } from '@/lib/status'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

const RECENT_PARAMS = { page: 0, size: 5, sort: 'updatedAt,desc' }

export function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'common'])
  const me = useCurrentUser()
  const canView = usePermission('approvals:view')
  const canRequest = usePermission('approvals:request')

  const cta = canRequest ? (
    <Button asChild>
      <Link to="/approvals/new">
        <Plus aria-hidden="true" />
        {t('cta.request')}
      </Link>
    </Button>
  ) : null

  return (
    <div className="space-y-8">
      <PageHeader title={t('greeting', { name: userDisplayName(me) })} description={t('description')} actions={cta} />
      {canView ? <ApprovalOverview cta={cta} /> : null}
      <div className={cn('grid gap-4', canView ? 'lg:grid-cols-3' : 'lg:grid-cols-2')}>
        {canView ? <PartnerStatusCard /> : null}
        <SystemHealthCard />
      </div>
    </div>
  )
}

function ApprovalOverview({ cta }: { cta: ReactNode }) {
  const { t } = useTranslation(['dashboard', 'common'])
  const fmt = useFormat()
  const stats = useCaseStats()
  const s = stats.data
  const loading = stats.isPending
  return (
    <>
      <section aria-label={t('title')} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard label={t('kpi.open')} icon={Inbox} value={s?.open} loading={loading} to={{ status: undefined }} />
        <KpiCard
          label={t('kpi.overdue')}
          icon={AlarmClock}
          value={s?.overdue}
          loading={loading}
          tone={s && s.overdue > 0 ? 'error' : undefined}
          hint={s && s.overdue > 0 ? t('kpi.overdueHint') : undefined}
          to={{ overdue: true }}
        />
        <KpiCard
          label={t('kpi.inReview')}
          icon={Eye}
          value={s?.inReview}
          loading={loading}
          to={{ status: ['IN_REVIEW'] }}
        />
        <KpiCard
          label={t('kpi.approvalRate')}
          icon={Gauge}
          display={s?.approvalRate != null ? fmt.percent(s.approvalRate) : undefined}
          loading={loading}
          hint={
            s
              ? [
                  s.averageLeadTimeDays != null
                    ? t('kpi.leadTime', { days: fmt.number(s.averageLeadTimeDays) })
                    : undefined,
                  t('kpi.approved30', { count: s.approvedLast30Days }),
                ]
                  .filter(Boolean)
                  .join(' · ')
              : undefined
          }
        />
      </section>
      <RecentCasesCard cta={cta} />
    </>
  )
}

function KpiCard({
  label,
  icon: Icon,
  value,
  display,
  loading,
  hint,
  tone,
  to,
}: {
  label: string
  icon: LucideIcon
  value?: number
  display?: string
  loading?: boolean
  hint?: string
  tone?: 'error'
  to?: { status?: ('IN_REVIEW' | 'SUBMITTED')[]; overdue?: boolean }
}) {
  const { t } = useTranslation(['dashboard', 'common'])
  const shown = display ?? (value !== undefined ? String(value) : undefined)
  const body = (
    <Card className={cn('h-full gap-3 py-5 transition-colors', to && 'group-hover:border-foreground/20')}>
      <CardHeader className="px-5">
        <CardDescription className="text-xs font-medium">{label}</CardDescription>
        <CardAction>
          <span
            style={tone ? statusStyle(tone) : undefined}
            className={cn(
              'flex size-7 items-center justify-center rounded-md bg-muted text-muted-foreground',
              tone && 'bg-(--sb-bg) text-(--sb-fg)',
            )}
          >
            <Icon aria-hidden="true" className="size-4" />
          </span>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-1 px-5">
        {loading ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <p
            className={cn(
              'text-3xl font-semibold tracking-tight tabular',
              tone && shown !== '0' && 'text-(--status-error-fg)',
            )}
          >
            {shown ?? t('common:notAvailable')}
          </p>
        )}
        <p className="min-h-4 text-xs text-muted-foreground">
          {loading ? ' ' : (hint ?? (shown === undefined ? t('kpi.noData') : ' '))}
        </p>
      </CardContent>
    </Card>
  )
  if (!to) return body
  return (
    <Link
      to="/approvals"
      search={to}
      className="group block rounded-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      {body}
    </Link>
  )
}

function RecentCasesCard({ cta }: { cta: ReactNode }) {
  const { t, i18n } = useTranslation(['dashboard', 'common'])
  const query = useCasePage(RECENT_PARAMS)
  const cases = query.data?.content ?? []
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('recent.title')}</CardTitle>
        <CardDescription>{t('recent.description')}</CardDescription>
        <CardAction>
          <Button variant="ghost" size="sm" asChild>
            <Link to="/approvals">
              {t('recent.all')}
              <ArrowRight aria-hidden="true" data-icon="inline-end" />
            </Link>
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        {query.isPending ? (
          <div className="space-y-2" aria-busy="true">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
        ) : cases.length === 0 ? (
          <EmptyState icon={FileCheck2} title={t('empty.title')} description={t('empty.description')} action={cta} />
        ) : (
          <ul className="divide-y">
            {cases.map((c) => (
              <li key={c.id}>
                <Link
                  to="/approvals/$caseId"
                  params={{ caseId: c.id }}
                  className="-mx-2 grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 rounded-md px-2 py-3 hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring sm:grid-cols-[9rem_minmax(0,1fr)_auto_auto]"
                >
                  <span className="flex items-center gap-2">
                    <span className="font-mono text-xs font-semibold">{c.caseNumber}</span>
                  </span>
                  <span className="col-span-2 min-w-0 sm:col-span-1">
                    <span className="block truncate text-sm">
                      <span className="font-mono text-xs">{c.part.partNumber}</span> ·{' '}
                      {localized(c.part, i18n.resolvedLanguage)}
                    </span>
                    <span className="flex items-center gap-2 truncate text-xs text-muted-foreground">
                      <RoleBadge role={c.ownRole} />
                      {c.partner.name ?? c.partner.bpnl}
                    </span>
                  </span>
                  <span className="text-xs">
                    <DueDate dueDate={c.dueDate} overdue={c.overdue} status={c.status} compact />
                  </span>
                  <span className="justify-self-end">
                    <CaseStatusBadge status={c.status} size="sm" />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}

function PartnerStatusCard() {
  const { t } = useTranslation(['dashboard', 'common'])
  const stats = useCaseStats()
  const partners = [...(stats.data?.byPartner ?? [])].sort((a, b) => b.open - a.open).slice(0, 8)
  return (
    <Card className="lg:col-span-2">
      <CardHeader>
        <CardTitle>{t('partners.title')}</CardTitle>
        <CardDescription>{t('partners.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        {stats.isPending ? (
          <Skeleton className="h-32 w-full" />
        ) : partners.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('partners.empty')}</p>
        ) : (
          <div className="relative overflow-x-auto rounded-md border">
            <table className="w-full text-sm" aria-label={t('partners.title')}>
              <thead className="bg-muted/60 text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="px-3 py-2 text-left font-medium">
                    {t('partners.partner')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">
                    {t('partners.open')}
                  </th>
                  <th scope="col" className="px-3 py-2 text-right font-medium">
                    {t('partners.overdue')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {partners.map((p) => (
                  <tr key={p.bpnl} className="border-t">
                    <td className="px-3 py-2">
                      <Link
                        to="/approvals"
                        search={{ partner: p.bpnl }}
                        className="rounded-sm font-medium underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
                      >
                        {p.name ?? p.bpnl}
                      </Link>
                      <span className="block font-mono text-[11px] text-muted-foreground">{p.bpnl}</span>
                    </td>
                    <td className="px-3 py-2 text-right tabular">{p.open}</td>
                    <td className="px-3 py-2 text-right tabular">
                      {p.overdue > 0 ? (
                        <span
                          style={statusStyle('error')}
                          className="inline-flex items-center gap-1 rounded-md bg-(--sb-bg) px-1.5 py-0.5 text-xs font-medium text-(--sb-fg)"
                        >
                          <AlarmClock aria-hidden="true" className="size-3" />
                          {p.overdue}
                        </span>
                      ) : (
                        <span className="text-muted-foreground">0</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
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
