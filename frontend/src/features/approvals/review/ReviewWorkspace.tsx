import { useQueryClient } from '@tanstack/react-query'
import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import {
  ArrowLeft,
  BadgeCheck,
  Circle,
  CircleX,
  Gavel,
  Loader2,
  Lock,
  Play,
  ShieldAlert,
  ShieldQuestion,
  TriangleAlert,
} from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from 'react-oidc-context'
import { toast } from 'sonner'

import { EmptyState } from '@/components/EmptyState'
import { SegmentedControl } from '@/components/SegmentedControl'
import { StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { useCurrentUser } from '@/features/auth/current-user'
import { statusToken } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import {
  approvalsApi,
  invalidateApprovals,
  useCase,
  useDecision,
  useSubmission,
  useTransition,
  type ApprovalCaseDetail,
  type DocumentDto,
  type FindingResult,
  type JsonValue,
  type Submission,
} from '../api'
import { CaseStatusBadge } from '../components/badges'
import { DocumentList, DocumentPreview } from '../components/Documents'
import { localized } from '../components/format'
import { problemCode, problemMessage, toastProblem } from '../components/problem'
import { IntegrityInfo } from '../components/RevisionView'
import { permittedActions } from '../model/actions'
import type { DecisionDraft, FindingDraft } from '../model/decision-rules'
import { caseStatusToken } from '../model/status'
import { latestSubmission } from '../model/submissions'
import { SchemaValueView } from '../schema-form/SchemaValueView'
import { DecisionPanel } from './DecisionPanel'
import { clearPendingDecision, peekPendingDecision, requestRecentAuth, savePendingDecision } from './recent-auth'

const routeApi = getRouteApi('/approvals/$caseId/review')
const FINDING_DEBOUNCE_MS = 800

/** Review workspace (UC-07): per-element findings (interim, unsigned) + signed overall decision. */
export function ReviewWorkspace() {
  const { t } = useTranslation('approvals')
  const { caseId } = routeApi.useParams()
  const caseQuery = useCase(caseId)
  const c = caseQuery.data
  const revision = c ? latestSubmission(c.submissions.filter((s) => s.status === 'SUBMITTED'))?.revision : undefined
  const submission = useSubmission(caseId, revision)

  if (caseQuery.isPending || (revision !== undefined && submission.isPending)) {
    return (
      <div className="space-y-5" aria-busy="true">
        <Skeleton className="h-16 w-96 max-w-full" />
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[17rem_minmax(0,1fr)_20rem]">
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-96 w-full" />
        </div>
      </div>
    )
  }
  if (!c || c.ownRole !== 'CUSTOMER' || !submission.data || !['SUBMITTED', 'IN_REVIEW'].includes(c.status)) {
    return (
      <EmptyState
        icon={Lock}
        title={t('review.notAvailable')}
        description={t('review.notAvailableDescription')}
        action={
          <Button variant="outline" size="sm" asChild>
            <Link to={c ? '/approvals/$caseId' : '/approvals'} params={{ caseId }}>
              <ArrowLeft aria-hidden="true" />
              {t('detail.back')}
            </Link>
          </Button>
        }
      />
    )
  }
  return <Review approvalCase={c} submission={submission.data} />
}

const FINDING_ICON = { ACCEPTED: BadgeCheck, NOT_ACCEPTED: CircleX, ACCEPTED_WITH_CONDITION: ShieldAlert }

function Review({ approvalCase: c, submission }: { approvalCase: ApprovalCaseDetail; submission: Submission }) {
  const { t, i18n } = useTranslation(['approvals', 'forms', 'status'])
  const me = useCurrentUser()
  const auth = useAuth()
  const navigate = useNavigate()
  const routeNavigate = routeApi.useNavigate()
  const search = routeApi.useSearch()
  const queryClient = useQueryClient()
  const transition = useTransition()
  const decide = useDecision()
  const lng = i18n.resolvedLanguage
  const actions = permittedActions(c.allowedActions, me.roles)
  const inReview = c.status === 'IN_REVIEW'
  const canDecide = inReview && actions.includes('DECIDE')
  const canReview = inReview && (actions.includes('REVIEW') || actions.includes('DECIDE'))

  const requestByCode = new Map(c.request.elements.map((e) => [e.code, e]))
  const elements = submission.elements
  const codes = elements.map((e) => e.code)
  const names = new Map(elements.map((e) => [e.code, localized(requestByCode.get(e.code)?.snapshot, lng) || e.code]))
  const selectedCode = codes.includes(search.element ?? '') ? search.element! : codes[0]
  const selected = elements.find((e) => e.code === selectedCode)

  const [findings, setFindings] = useState<Record<string, FindingDraft | undefined>>(() =>
    Object.fromEntries(
      elements.map((e) => {
        const finding = e.finding ?? c.reviewFindings.find((f) => f.code === e.code)
        return [e.code, finding ? { result: finding.result, comment: finding.comment ?? '' } : undefined]
      }),
    ),
  )
  // resume after re-login (RECENT_AUTH_REQUIRED): restore the pending decision and ask again
  const [pending] = useState(() => peekPendingDecision(c.id))
  const [decision, setDecision] = useState<DecisionDraft>(pending ?? { conditions: [] })
  const [confirmOpen, setConfirmOpen] = useState(Boolean(pending))
  const [resumed, setResumed] = useState(Boolean(pending))
  useEffect(() => clearPendingDecision(c.id), [c.id])

  // --- interim findings: serialised PUTs with If-Match -----------------------------------------
  const version = useRef(c.version)
  const chain = useRef<Promise<unknown>>(Promise.resolve())
  const timers = useRef<Record<string, number>>({})
  const [saving, setSaving] = useState<Record<string, 'saving' | 'saved' | 'error'>>({})

  const persist = useCallback(
    (code: string, finding: FindingDraft) => {
      if (!finding.result) return
      setSaving((s) => ({ ...s, [code]: 'saving' }))
      chain.current = chain.current
        .then(async () => {
          try {
            const res = await approvalsApi.putReviewElement(
              c.id,
              code,
              { result: finding.result!, comment: finding.comment ?? '' },
              version.current,
            )
            version.current = res.version ?? (await approvalsApi.getCase(c.id)).version
            setSaving((s) => ({ ...s, [code]: 'saved' }))
          } catch (error) {
            if (problemCode(error) === 'VERSION_CONFLICT') {
              version.current = (await approvalsApi.getCase(c.id)).version
            }
            setSaving((s) => ({ ...s, [code]: 'error' }))
            toastProblem(error, t('review.findingSaveFailed'))
          }
        })
        .catch(() => undefined)
    },
    [c.id, t],
  )

  const updateFinding = (code: string, patch: Partial<FindingDraft>, immediate: boolean) => {
    const next = { ...findings[code], ...patch }
    setFindings((prev) => ({ ...prev, [code]: next }))
    window.clearTimeout(timers.current[code])
    if (immediate) persist(code, next)
    else timers.current[code] = window.setTimeout(() => persist(code, next), FINDING_DEBOUNCE_MS)
  }

  const select = (code: string) => void routeNavigate({ search: { element: code }, replace: true })

  const startReview = () =>
    transition.mutate(
      { id: c.id, action: 'start-review', version: c.version },
      {
        onSuccess: () => toast.success(t('actions.startReviewDone', { caseNumber: c.caseNumber })),
        onError: (error) => toastProblem(error, t('actions.failed')),
      },
    )

  const submitDecision = async () => {
    await chain.current
    decide.mutate(
      {
        id: c.id,
        version: version.current,
        input: {
          result: decision.result!,
          conditions:
            decision.result === 'CONDITIONALLY_APPROVED'
              ? decision.conditions.filter((x) => x.trim()).map((text) => ({ text: text.trim() }))
              : [],
          conditionDueDate: decision.result === 'CONDITIONALLY_APPROVED' ? decision.conditionDueDate : undefined,
          comment: decision.comment?.trim() || undefined,
          confirmation: true,
        },
      },
      {
        onSuccess: () => {
          toast.success(t('review.decided', { caseNumber: c.caseNumber }))
          setConfirmOpen(false)
          void navigate({ to: '/approvals/$caseId', params: { caseId: c.id }, search: { tab: 'decision' } })
        },
        onError: (error) => {
          if (problemCode(error) === 'RECENT_AUTH_REQUIRED') {
            savePendingDecision(c.id, decision)
            void requestRecentAuth(auth)
          } else if (problemCode(error) === 'VERSION_CONFLICT') {
            void invalidateApprovals(queryClient)
          }
        },
      },
    )
  }

  const findingOf = (code: string) => findings[code]?.result

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div className="space-y-2">
          <Button variant="ghost" size="sm" className="-ml-2" asChild>
            <Link to="/approvals/$caseId" params={{ caseId: c.id }}>
              <ArrowLeft aria-hidden="true" />
              {c.caseNumber}
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">{t('review.title')}</h1>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <CaseStatusBadge status={c.status} size="sm" />
            <span>{t('revisions.revisionTitle', { revision: submission.revision })}</span>
            <span>
              <span className="font-mono">{c.part.partNumber}</span> · {c.partner.name ?? c.partner.bpnl}
            </span>
          </div>
        </div>
        <IntegrityInfo caseNumber={c.caseNumber} caseId={c.id} submission={submission} />
      </div>

      {c.status === 'SUBMITTED' ? (
        <Alert>
          <Play aria-hidden="true" />
          <AlertTitle>{t('review.startTitle')}</AlertTitle>
          <AlertDescription>
            <p>{t('review.startDescription')}</p>
            {actions.includes('START_REVIEW') ? (
              <Button size="sm" className="mt-2" onClick={startReview} disabled={transition.isPending}>
                {transition.isPending ? (
                  <Loader2 aria-hidden="true" className="animate-spin" />
                ) : (
                  <Play aria-hidden="true" />
                )}
                {t('actions.startReview')}
              </Button>
            ) : null}
          </AlertDescription>
        </Alert>
      ) : null}
      {submission.signature?.status === 'UNVERIFIED' ? (
        <Alert>
          <ShieldQuestion aria-hidden="true" className="text-(--status-unverified-solid)" />
          <AlertTitle>{t('review.signatureUnverifiedTitle')}</AlertTitle>
          <AlertDescription>{t('review.signatureUnverifiedDescription')}</AlertDescription>
        </Alert>
      ) : null}
      {submission.signature?.status === 'INVALID' ? (
        <Alert variant="destructive">
          <TriangleAlert aria-hidden="true" />
          <AlertTitle>{t('review.signatureInvalidTitle')}</AlertTitle>
          <AlertDescription>{t('review.signatureInvalidDescription')}</AlertDescription>
        </Alert>
      ) : null}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[17rem_minmax(0,1fr)_20rem]">
        <nav aria-label={t('review.elementList')} className="h-fit rounded-lg border bg-card p-2">
          <p className="px-2.5 pt-1 pb-1.5 text-xs text-muted-foreground tabular">
            {t('review.reviewed', { done: codes.filter((code) => findingOf(code)).length, total: codes.length })}
          </p>
          <ul className="max-h-[40svh] space-y-0.5 relative overflow-y-auto xl:max-h-[70svh]">
            {elements.map((e) => {
              const result = findingOf(e.code)
              const Icon = result ? FINDING_ICON[result] : Circle
              const active = e.code === selectedCode
              return (
                <li key={e.code}>
                  <button
                    type="button"
                    onClick={() => select(e.code)}
                    aria-current={active ? 'true' : undefined}
                    className={cn(
                      'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring',
                      active && 'bg-participant-soft text-participant-soft-foreground hover:bg-participant-soft',
                    )}
                  >
                    <Icon
                      aria-hidden="true"
                      className="size-4 shrink-0"
                      style={result ? { color: `var(--status-${statusToken('finding', result)}-solid)` } : undefined}
                    />
                    <span className="sr-only">{result ? t(`status:finding.${result}`) : t('review.noFinding')}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-mono text-[11px] text-muted-foreground">{e.code}</span>
                      <span className="block truncate text-sm">{names.get(e.code)}</span>
                    </span>
                    {e.selfAssessment && e.selfAssessment !== 'OK' ? (
                      <StatusBadge kind="assessment" status={e.selfAssessment} size="sm" />
                    ) : null}
                  </button>
                </li>
              )
            })}
          </ul>
        </nav>

        <Card className="min-w-0">
          <CardContent>
            {selected ? (
              <ElementReview
                key={selected.code}
                approvalCase={c}
                element={selected}
                name={names.get(selected.code) ?? selected.code}
                finding={findings[selected.code]}
                canReview={canReview}
                saving={saving[selected.code]}
                onFinding={(patch, immediate) => updateFinding(selected.code, patch, immediate)}
              />
            ) : null}
          </CardContent>
        </Card>

        <div className="space-y-4 xl:sticky xl:top-20 xl:self-start">
          {inReview ? (
            <DecisionPanel
              draft={decision}
              onChange={setDecision}
              findings={findings}
              elementCodes={codes}
              names={names}
              disabled={!canDecide}
              onDecide={() => setConfirmOpen(true)}
              onSelectElement={select}
            />
          ) : null}
        </div>
      </div>

      <ConfirmDecisionDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          setConfirmOpen(open)
          if (!open) {
            setResumed(false)
            decide.reset()
          }
        }}
        approvalCase={c}
        revision={submission.revision}
        decision={decision}
        findings={findings}
        codes={codes}
        resumed={resumed}
        pending={decide.isPending}
        error={
          decide.error && problemCode(decide.error) !== 'RECENT_AUTH_REQUIRED'
            ? problemMessage(decide.error)
            : undefined
        }
        reauthenticating={problemCode(decide.error) === 'RECENT_AUTH_REQUIRED'}
        onConfirm={() => void submitDecision()}
      />
    </div>
  )
}

function ElementReview({
  approvalCase,
  element,
  name,
  finding,
  canReview,
  saving,
  onFinding,
}: {
  approvalCase: ApprovalCaseDetail
  element: Submission['elements'][number]
  name: string
  finding: FindingDraft | undefined
  canReview: boolean
  saving: 'saving' | 'saved' | 'error' | undefined
  onFinding: (patch: Partial<FindingDraft>, immediate: boolean) => void
}) {
  const { t } = useTranslation('approvals')
  const commentId = useId()
  const request = approvalCase.request.elements.find((e) => e.code === element.code)
  const [previewDoc, setPreviewDoc] = useState<DocumentDto | undefined>(element.documents[0])
  const commentRequired = finding?.result === 'NOT_ACCEPTED' || finding?.result === 'ACCEPTED_WITH_CONDITION'

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-xs text-muted-foreground">{element.code}</span>
          {request ? <StatusBadge kind="requirement" status={request.requirement} size="sm" /> : null}
        </div>
        <h2 className="text-lg font-semibold tracking-tight">{name}</h2>
      </header>

      <dl className="grid gap-4 sm:grid-cols-[12rem_minmax(0,1fr)]">
        <div className="space-y-1">
          <dt className="text-xs text-muted-foreground">{t('review.supplierAssessment')}</dt>
          <dd>
            {element.selfAssessment ? (
              <StatusBadge kind="assessment" status={element.selfAssessment} />
            ) : (
              <span className="text-sm text-muted-foreground">{t('elements.notAssessed')}</span>
            )}
          </dd>
        </div>
        <div className="space-y-1">
          <dt className="text-xs text-muted-foreground">{t('review.supplierComment')}</dt>
          <dd className="text-sm whitespace-pre-line">{element.comment || '–'}</dd>
        </div>
      </dl>

      {request?.snapshot.valueSchema ? (
        <section className="space-y-2" aria-label={t('package.values')}>
          <h3 className="text-sm font-medium">{t('package.values')}</h3>
          <SchemaValueView
            schema={request.snapshot.valueSchema}
            value={element.values as JsonValue | undefined}
            label={name}
          />
        </section>
      ) : null}

      <section className="space-y-3 rounded-lg border p-4" aria-labelledby={`${commentId}-finding`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id={`${commentId}-finding`} className="text-sm font-semibold">
            {t('review.finding')}
          </h3>
          <span className="text-xs text-muted-foreground" aria-live="polite">
            {saving === 'saving' ? t('package.saving') : saving === 'saved' ? t('review.interimSaved') : null}
          </span>
        </div>
        <SegmentedControl<FindingResult>
          label={t('review.finding')}
          value={finding?.result}
          disabled={!canReview}
          onValueChange={(result) => onFinding({ result }, true)}
          options={[
            { value: 'ACCEPTED', label: t('review.findings.ACCEPTED'), icon: BadgeCheck, token: 'accepted' },
            { value: 'NOT_ACCEPTED', label: t('review.findings.NOT_ACCEPTED'), icon: CircleX, token: 'not-accepted' },
            {
              value: 'ACCEPTED_WITH_CONDITION',
              label: t('review.findings.ACCEPTED_WITH_CONDITION'),
              icon: ShieldAlert,
              token: 'accepted-with-condition',
            },
          ]}
        />
        <div className="grid gap-1.5">
          <Label htmlFor={commentId} className="gap-1">
            {t('review.findingComment')}
            {commentRequired ? (
              <span aria-hidden="true" className="text-destructive">
                *
              </span>
            ) : null}
          </Label>
          <Textarea
            id={commentId}
            rows={3}
            disabled={!canReview || !finding?.result}
            value={finding?.comment ?? ''}
            aria-invalid={(finding?.result === 'NOT_ACCEPTED' && !finding.comment?.trim()) || undefined}
            placeholder={commentRequired ? t('review.findingCommentRequired') : undefined}
            onChange={(e) => onFinding({ comment: e.target.value }, false)}
          />
        </div>
        {!canReview ? <p className="text-xs text-muted-foreground">{t('review.readOnlyHint')}</p> : null}
      </section>

      <section className="space-y-3" aria-label={t('package.documents')}>
        <h3 className="text-sm font-medium">{t('package.documents')}</h3>
        <DocumentList documents={element.documents} onPreview={setPreviewDoc} />
        {previewDoc ? (
          <div className="rounded-lg border bg-muted/30 p-3">
            <p className="mb-2 truncate text-xs font-medium">{previewDoc.fileName}</p>
            <DocumentPreview key={previewDoc.id} doc={previewDoc} />
          </div>
        ) : null}
      </section>
    </div>
  )
}

function ConfirmDecisionDialog({
  open,
  onOpenChange,
  approvalCase,
  revision,
  decision,
  findings,
  codes,
  resumed,
  pending,
  error,
  reauthenticating,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  approvalCase: ApprovalCaseDetail
  revision: number
  decision: DecisionDraft
  findings: Record<string, FindingDraft | undefined>
  codes: string[]
  resumed: boolean
  pending: boolean
  error?: string
  reauthenticating: boolean
  onConfirm: () => void
}) {
  const { t } = useTranslation(['approvals', 'forms', 'status'])
  const id = useId()
  const [confirmed, setConfirmed] = useState(false)
  const counts = { ACCEPTED: 0, NOT_ACCEPTED: 0, ACCEPTED_WITH_CONDITION: 0 }
  for (const code of codes) {
    const r = findings[code]?.result
    if (r) counts[r] += 1
  }
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setConfirmed(false)
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Gavel aria-hidden="true" className="size-4" />
            {t('confirmDecision.title')}
          </DialogTitle>
          <DialogDescription>
            {t('confirmDecision.description', { caseNumber: approvalCase.caseNumber, revision })}
          </DialogDescription>
        </DialogHeader>
        {resumed ? (
          <Alert>
            <AlertDescription>{t('confirmDecision.resumed')}</AlertDescription>
          </Alert>
        ) : null}
        <div className="space-y-3 rounded-lg border p-4 text-sm">
          {decision.result ? <StatusBadge kind="approval" status={caseStatusToken(decision.result)} /> : null}
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
            {(Object.keys(counts) as (keyof typeof counts)[]).map((k) => (
              <li key={k}>
                {t(`status:finding.${k}`)}: <span className="font-medium text-foreground tabular">{counts[k]}</span>
              </li>
            ))}
          </ul>
          {decision.result === 'CONDITIONALLY_APPROVED' ? (
            <div>
              <p className="text-xs text-muted-foreground">{t('decisionPanel.conditions')}</p>
              <ol className="list-decimal pl-5">
                {decision.conditions
                  .filter((c) => c.trim())
                  .map((c, i) => (
                    <li key={i}>{c}</li>
                  ))}
              </ol>
            </div>
          ) : null}
          {decision.comment ? <p className="whitespace-pre-line">{decision.comment}</p> : null}
        </div>
        <div className="flex items-start gap-3 rounded-lg border bg-muted/40 p-3">
          <Checkbox id={id} checked={confirmed} onCheckedChange={(c) => setConfirmed(c === true)} className="mt-0.5" />
          <Label htmlFor={id} className="text-sm leading-snug font-normal">
            {t('confirmDecision.confirm')}
          </Label>
        </div>
        <p className="text-xs text-muted-foreground">{t('confirmDecision.signatureHint')}</p>
        {reauthenticating ? (
          <p role="status" className="flex items-center gap-2 text-sm">
            <Loader2 aria-hidden="true" className="size-4 animate-spin" />
            {t('confirmDecision.reauth')}
          </p>
        ) : null}
        {error ? (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('forms:cancel')}
          </Button>
          <Button onClick={onConfirm} disabled={!confirmed || pending || reauthenticating}>
            {pending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Gavel aria-hidden="true" />}
            {t('confirmDecision.submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
