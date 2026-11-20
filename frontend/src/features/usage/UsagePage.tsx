import { useMutation } from '@tanstack/react-query'
import { BarChart3, Calculator, FileDown, Loader2, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { saveBlob } from '@/features/approvals/components/documents'
import { useFormat } from '@/features/approvals/components/format'
import { toastProblem } from '@/features/approvals/components/problem'

import { exportUsageCsv, useRecomputeUsage, useUsage, type UsageMonth, type UsageRange } from './api'
import { UsageChart } from './UsageChart'
import { fillMonths, formatMonth, lastMonths, monthKey, totals } from './usage-model'

const PERIODS = [6, 12, 24] as const
type Period = (typeof PERIODS)[number]

/** Usage metering (UC-12): approval cases and revisions per month, CSV export, reproducible recompute. */
export function UsagePage() {
  const { t, i18n } = useTranslation('usage')
  const fmt = useFormat()
  const [period, setPeriod] = useState<Period>(12)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const range: UsageRange = lastMonths(period, monthKey(new Date()))
  const query = useUsage(range)
  const recompute = useRecomputeUsage()
  const exportCsv = useMutation({
    meta: { silent: true },
    mutationFn: () => exportUsageCsv(range),
    onSuccess: (blob) => saveBlob(blob, `usage-${range.from}-${range.to}.csv`),
    onError: (error) => toastProblem(error, t('exportFailed')),
  })
  const months = query.data ? fillMonths(query.data, range.from, range.to) : []
  const sum = totals(months)
  const current = months.at(-1)
  const summary = t('chartSummary', {
    from: formatMonth(range.from, i18n.resolvedLanguage, 'long'),
    to: formatMonth(range.to, i18n.resolvedLanguage, 'long'),
    cases: sum.approvalCases,
    revisions: sum.revisions,
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('title')}
        description={t('description')}
        actions={
          <>
            <Button
              variant="outline"
              onClick={() => exportCsv.mutate()}
              disabled={exportCsv.isPending}
              data-action="usage-export"
            >
              {exportCsv.isPending ? (
                <Loader2 aria-hidden="true" className="animate-spin" />
              ) : (
                <FileDown aria-hidden="true" />
              )}
              {t('export')}
            </Button>
            <Button
              variant="outline"
              onClick={() => setConfirmOpen(true)}
              disabled={recompute.isPending}
              data-action="usage-recompute"
            >
              {recompute.isPending ? (
                <Loader2 aria-hidden="true" className="animate-spin" />
              ) : (
                <RefreshCw aria-hidden="true" />
              )}
              {t('recompute')}
            </Button>
          </>
        }
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryTile
          label={t('summary.cases', { months: period })}
          value={query.isPending ? undefined : sum.approvalCases}
        />
        <SummaryTile
          label={t('summary.revisions', { months: period })}
          value={query.isPending ? undefined : sum.revisions}
        />
        <SummaryTile
          label={t('summary.currentMonth', { month: formatMonth(range.to, i18n.resolvedLanguage, 'long') })}
          value={query.isPending ? undefined : (current?.approvalCases ?? 0)}
        />
      </div>

      <Card>
        <CardHeader className="flex flex-wrap items-start justify-between gap-3 sm:flex-row">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2">
              <BarChart3 aria-hidden="true" className="size-4 text-muted-foreground" />
              {t('chartTitle')}
            </CardTitle>
            <CardDescription>{t('definition')}</CardDescription>
          </div>
          <Select value={String(period)} onValueChange={(v) => setPeriod(Number(v) as Period)}>
            <SelectTrigger size="sm" className="w-44" aria-label={t('period')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PERIODS.map((p) => (
                <SelectItem key={p} value={String(p)}>
                  {t('periodMonths', { count: p })}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent className="space-y-6">
          {query.isPending ? (
            <Skeleton className="h-56 w-full" />
          ) : query.isError ? (
            <EmptyState
              icon={BarChart3}
              title={t('loadError')}
              action={
                <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
                  {t('retry')}
                </Button>
              }
            />
          ) : (
            <>
              <UsageChart data={months} summary={summary} />
              <UsageTable months={months} fmt={fmt} />
            </>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t('recomputeConfirm.title')}
        description={t('recomputeConfirm.description')}
        confirmLabel={t('recomputeConfirm.confirm')}
        onConfirm={() => {
          setConfirmOpen(false)
          recompute.mutate(undefined, {
            onSuccess: (result) =>
              result && !result.consistent
                ? toast.warning(t('recomputeDifferences', { count: result.differences.length }), {
                    description: result.differences
                      .slice(0, 3)
                      .map((d) => `${d.month} ${d.metric}: ${d.before} → ${d.after}`)
                      .join(' · '),
                    duration: 15_000,
                  })
                : toast.success(t('recomputeDone'), {
                    description: result ? t('recomputeConsistent', { count: result.events }) : undefined,
                  }),
            onError: (error) => toastProblem(error, t('recomputeFailed')),
          })
        }}
      />
    </div>
  )
}

function SummaryTile({ label, value }: { label: string; value?: number }) {
  return (
    <Card className="gap-2 py-5">
      <CardContent className="space-y-1 px-5">
        <p className="text-xs font-medium text-muted-foreground">{label}</p>
        {value === undefined ? (
          <Skeleton className="h-8 w-16" />
        ) : (
          <p className="text-3xl font-semibold tracking-tight tabular">{value}</p>
        )}
      </CardContent>
    </Card>
  )
}

function UsageTable({ months, fmt }: { months: UsageMonth[]; fmt: ReturnType<typeof useFormat> }) {
  const { t, i18n } = useTranslation('usage')
  const d = (m: UsageMonth, key: string) => m.details[key] ?? 0
  const rows = [...months].reverse()
  return (
    <div className="relative overflow-x-auto rounded-md border">
      <table className="w-full text-sm" data-testid="usage-table">
        <caption className="sr-only">{t('tableCaption')}</caption>
        <thead className="bg-muted/60 text-xs text-muted-foreground">
          <tr>
            <th scope="col" className="px-3 py-2 text-left font-medium">
              {t('columns.month')}
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              {t('columns.approvalCases')}
            </th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium md:table-cell">
              {t('columns.submitted')}
            </th>
            <th scope="col" className="hidden px-3 py-2 text-right font-medium md:table-cell">
              {t('columns.received')}
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              {t('columns.revisions')}
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((m) => (
            <tr key={m.month} className="border-t" data-month={m.month}>
              <th scope="row" className="px-3 py-2 text-left font-medium">
                {formatMonth(m.month, i18n.resolvedLanguage, 'long')}
              </th>
              <td className="px-3 py-2 text-right font-semibold tabular">{fmt.number(m.approvalCases)}</td>
              <td className="hidden px-3 py-2 text-right text-muted-foreground tabular md:table-cell">
                {fmt.number(d(m, 'APPROVAL_CASES_SUBMITTED'))}
              </td>
              <td className="hidden px-3 py-2 text-right text-muted-foreground tabular md:table-cell">
                {fmt.number(d(m, 'APPROVAL_CASES_RECEIVED'))}
              </td>
              <td className="px-3 py-2 text-right tabular">{fmt.number(m.revisions)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="flex items-center gap-1.5 border-t px-3 py-2 text-xs text-muted-foreground">
        <Calculator aria-hidden="true" className="size-3.5" />
        {t('recomputeHint')}
      </p>
    </div>
  )
}
