import { getRouteApi, Link } from '@tanstack/react-router'
import { ArrowLeft, FileX2, Gavel, History, SearchX } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ClarificationBanner } from '@/features/exchange/clarification/ClarificationBanner'
import { ExchangeModeBadge } from '@/features/exchange/components/ExchangeModeBadge'
import { ExchangeTab } from '@/features/exchange/components/ExchangeTab'
import { AuditTrail } from '@/features/masterdata/shared/AuditTrail'
import { cn } from '@/lib/utils'

import { useCase, useSubmission, type ApprovalCaseDetail } from '../api'
import { CaseStatusBadge, SignatureBadge } from '../components/badges'
import { DecisionView } from '../components/DecisionView'
import { useFormat } from '../components/format'
import { RevisionView } from '../components/RevisionView'
import { Timeline } from '../components/Timeline'
import { decidedRevision, latestSubmission, revisionExchangeMode } from '../model/submissions'
import { CASE_AUDIT_OBJECT_TYPE, CASE_TABS, type CaseTab } from './case-tabs'
import { CaseHeader } from './CaseHeader'
import { ElementsTab } from './ElementsTab'

const routeApi = getRouteApi('/approvals/$caseId/')

export function CaseDetailPage() {
  const { t } = useTranslation('approvals')
  const { caseId } = routeApi.useParams()
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const query = useCase(caseId)

  if (query.isPending) return <DetailSkeleton />
  if (query.isError || !query.data) {
    return (
      <EmptyState
        icon={SearchX}
        title={t('detail.notFound')}
        action={
          <Button variant="outline" size="sm" asChild>
            <Link to="/approvals">
              <ArrowLeft aria-hidden="true" />
              {t('detail.back')}
            </Link>
          </Button>
        }
      />
    )
  }
  const c = query.data
  const tab: CaseTab = search.tab ?? 'elements'

  return (
    <div className="space-y-6">
      <Button variant="ghost" size="sm" className="-ml-2" asChild>
        <Link to="/approvals">
          <ArrowLeft aria-hidden="true" />
          {t('list.title')}
        </Link>
      </Button>
      <ClarificationBanner approvalCase={c} />
      <CaseHeader approvalCase={c} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Tabs
          value={tab}
          onValueChange={(v) =>
            void navigate({
              search: (prev) => ({ ...prev, tab: v === 'elements' ? undefined : (v as CaseTab) }),
              replace: true,
            })
          }
          className="min-w-0"
        >
          <div className="-mx-1 relative overflow-x-auto px-1">
            <TabsList>
              {CASE_TABS.map((key) => (
                <TabsTrigger key={key} value={key}>
                  {t(`tabs.${key}`)}
                  {key === 'revisions' && c.submissions.length > 0 ? (
                    <span className="ml-1 rounded-full bg-muted px-1.5 text-[11px] tabular">
                      {c.submissions.length}
                    </span>
                  ) : null}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>
          <TabsContent value="elements" className="pt-2">
            <ElementsTab approvalCase={c} />
          </TabsContent>
          <TabsContent value="revisions" className="pt-2">
            <RevisionsTab
              approvalCase={c}
              selected={search.revision}
              onSelect={(revision) => void navigate({ search: (prev) => ({ ...prev, revision }), replace: true })}
            />
          </TabsContent>
          <TabsContent value="decision" className="pt-2">
            <DecisionTab approvalCase={c} />
          </TabsContent>
          <TabsContent value="exchange" className="pt-2">
            <ExchangeTab approvalCase={c} />
          </TabsContent>
          <TabsContent value="audit" className="pt-2">
            <Card>
              <CardContent>
                <AuditTrail objectType={CASE_AUDIT_OBJECT_TYPE} objectId={c.id} />
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>

        <Card className="h-fit gap-4">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-sm">
              <History aria-hidden="true" className="size-4 text-muted-foreground" />
              {t('timeline.title')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Timeline entries={c.timeline} />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function DetailSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true">
      <Skeleton className="h-7 w-40" />
      <Skeleton className="h-52 w-full" />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Skeleton className="h-80 w-full" />
        <Skeleton className="h-80 w-full" />
      </div>
    </div>
  )
}

function RevisionsTab({
  approvalCase: c,
  selected,
  onSelect,
}: {
  approvalCase: ApprovalCaseDetail
  selected: number | undefined
  onSelect: (revision: number) => void
}) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  const revisions = [...c.submissions].sort((a, b) => b.revision - a.revision)
  if (revisions.length === 0) {
    return <EmptyState icon={FileX2} title={t('revisions.empty')} description={t('revisions.emptyDescription')} />
  }
  const current = selected ?? latestSubmission(c.submissions)?.revision ?? revisions[0]!.revision
  const selectedSummary = revisions.find((r) => r.revision === current) ?? revisions[0]!

  return (
    <div className="grid gap-4 md:grid-cols-[14rem_minmax(0,1fr)]">
      <nav aria-label={t('revisions.list')}>
        <ul className="flex gap-2 relative overflow-x-auto md:flex-col">
          {revisions.map((r) => {
            const active = r.revision === selectedSummary.revision
            const mode = revisionExchangeMode(r)
            return (
              <li key={r.revision} className="shrink-0">
                <button
                  type="button"
                  aria-current={active ? 'true' : undefined}
                  onClick={() => onSelect(r.revision)}
                  className={cn(
                    'w-full space-y-1.5 rounded-lg border px-3 py-2.5 text-left transition-colors hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring',
                    active && 'border-participant/50 bg-participant-soft/60',
                  )}
                >
                  <span className="flex items-center justify-between gap-2 text-sm font-medium">
                    {t('revisions.revisionTitle', { revision: r.revision })}
                    {r.decisionResult ? <CaseStatusBadge status={r.decisionResult} size="sm" /> : null}
                  </span>
                  <span className="block text-xs text-muted-foreground">
                    {r.status === 'SUBMITTED' ? fmt.dateTime(r.submittedAt) : t('revisions.draft')}
                  </span>
                  {r.status === 'SUBMITTED' ? (
                    <span className="flex flex-wrap items-center gap-1.5">
                      <SignatureBadge status={r.signature?.status ?? 'NOT_SIGNED'} size="sm" />
                      {mode ? <ExchangeModeBadge mode={mode} interactive={false} /> : null}
                    </span>
                  ) : null}
                </button>
              </li>
            )
          })}
        </ul>
      </nav>
      <RevisionView
        key={selectedSummary.revision}
        approvalCase={c}
        revision={selectedSummary.status === 'DRAFT' && c.ownRole === 'SUPPLIER' ? 'draft' : selectedSummary.revision}
      />
    </div>
  )
}

function DecisionTab({ approvalCase: c }: { approvalCase: ApprovalCaseDetail }) {
  const { t } = useTranslation('approvals')
  const revision = c.lastDecision ? undefined : decidedRevision(c.submissions)
  const submission = useSubmission(c.id, revision)
  const decision = c.lastDecision ?? submission.data?.decision ?? undefined
  if (revision !== undefined && submission.isPending) return <Skeleton className="h-48 w-full" />
  if (!decision) {
    return <EmptyState icon={Gavel} title={t('decision.empty')} description={t('decision.emptyDescription')} />
  }
  return <DecisionView approvalCase={c} decision={decision} />
}
