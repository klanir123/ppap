import { useMutation } from '@tanstack/react-query'
import { ChevronRight, Download, FileJson, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { StatusBadge } from '@/components/StatusBadge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

import { ExchangeModeBadge } from '@/features/exchange/components/ExchangeModeBadge'

import { approvalsApi, useSubmission, type ApprovalCaseDetail, type JsonValue, type Submission } from '../api'
import { revisionExchangeMode } from '../model/submissions'
import { SchemaValueView } from '../schema-form/SchemaValueView'
import { HashText, SignatureBadge } from './badges'
import { DocumentList } from './Documents'
import { saveBlob } from './documents'
import { localized, useFormat } from './format'
import { toastProblem } from './problem'

/** Integrity block of a submitted revision: hash, signature, canonical payload download. */
export function IntegrityInfo({
  caseNumber,
  caseId,
  submission,
  kind = 'submission',
}: {
  caseNumber: string
  caseId: string
  submission: Pick<Submission, 'revision' | 'payloadHash' | 'signature'>
  kind?: 'submission' | 'decision'
}) {
  const { t } = useTranslation('approvals')
  const download = useMutation({
    mutationFn: () =>
      kind === 'decision'
        ? approvalsApi.getDecisionPayload(caseId, submission.revision)
        : approvalsApi.getSubmissionPayload(caseId, submission.revision),
    onSuccess: ({ blob }) =>
      saveBlob(blob, `${caseNumber}-rev${submission.revision}-${kind === 'decision' ? 'decision' : 'submission'}.json`),
    onError: (error) => toastProblem(error, t('revisions.downloadFailed')),
    meta: { silent: true },
  })
  return (
    <dl className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
      <div className="flex items-center gap-2">
        <dt className="text-xs text-muted-foreground">{t('revisions.payloadHash')}</dt>
        <dd>{submission.payloadHash ? <HashText hash={submission.payloadHash} /> : '–'}</dd>
      </div>
      <div className="flex items-center gap-2">
        <dt className="text-xs text-muted-foreground">{t('revisions.signature')}</dt>
        <dd>
          <SignatureBadge status={submission.signature?.status ?? 'NOT_SIGNED'} size="sm" />
        </dd>
      </div>
      {submission.payloadHash ? (
        <div>
          <dt className="sr-only">{t('revisions.payload')}</dt>
          <dd>
            <Button variant="outline" size="sm" onClick={() => download.mutate()} disabled={download.isPending}>
              {download.isPending ? (
                <Loader2 aria-hidden="true" className="animate-spin" />
              ) : (
                <FileJson aria-hidden="true" />
              )}
              {kind === 'decision' ? t('revisions.downloadDecision') : t('revisions.downloadPayload')}
            </Button>
          </dd>
        </div>
      ) : null}
    </dl>
  )
}

/** Read-only view of one revision (elements with assessment, values, comment, documents). */
export function RevisionView({
  approvalCase,
  revision,
}: {
  approvalCase: ApprovalCaseDetail
  revision: number | 'draft'
}) {
  const { t, i18n } = useTranslation('approvals')
  const fmt = useFormat()
  const query = useSubmission(approvalCase.id, revision)
  const lng = i18n.resolvedLanguage

  if (query.isPending) {
    return (
      <div className="space-y-3" aria-busy="true">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    )
  }
  if (query.isError || !query.data) return <p className="text-sm text-destructive">{t('revisions.loadError')}</p>
  const s = query.data
  const snapshots = new Map(approvalCase.request.elements.map((e) => [e.code, e]))
  const mode = revisionExchangeMode(approvalCase.submissions.find((r) => r.revision === s.revision))

  return (
    <div className="space-y-4">
      <div className="space-y-3 rounded-lg border bg-muted/30 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold">
            {t('revisions.revisionTitle', { revision: s.revision })}
            {s.basedOnRevision ? (
              <span className="ml-2 text-xs font-normal text-muted-foreground">
                {t('revisions.basedOn', { revision: s.basedOnRevision })}
              </span>
            ) : null}
          </h3>
          <span className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            {s.status === 'SUBMITTED'
              ? t('revisions.submittedBy', { at: fmt.dateTime(s.submittedAt), by: s.submittedBy ?? '–' })
              : t('revisions.draft')}
            {s.status === 'SUBMITTED' && mode ? <ExchangeModeBadge mode={mode} /> : null}
          </span>
        </div>
        {s.status === 'SUBMITTED' ? (
          <IntegrityInfo caseNumber={approvalCase.caseNumber} caseId={approvalCase.id} submission={s} />
        ) : null}
      </div>
      <ul className="space-y-2">
        {s.elements.map((el) => {
          const snap = snapshots.get(el.code)
          const name = snap ? localized(snap.snapshot, lng) : el.code
          return (
            <li key={el.code}>
              <details className="group rounded-lg border bg-card">
                <summary className="flex cursor-pointer list-none items-center gap-3 rounded-lg px-3 py-2.5 hover:bg-muted/40 focus-visible:outline-2 focus-visible:outline-ring [&::-webkit-details-marker]:hidden">
                  <ChevronRight
                    aria-hidden="true"
                    className="size-4 shrink-0 text-muted-foreground transition-transform group-open:rotate-90"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="font-mono text-xs text-muted-foreground">{el.code}</span>
                    <span className="block truncate text-sm font-medium">{name}</span>
                  </span>
                  {el.selfAssessment ? (
                    <StatusBadge kind="assessment" status={el.selfAssessment} size="sm" />
                  ) : (
                    <span className="text-xs text-muted-foreground">{t('elements.notAssessed')}</span>
                  )}
                </summary>
                <div className="space-y-4 border-t px-4 py-4">
                  {el.comment ? (
                    <div className="space-y-1">
                      <p className="text-xs text-muted-foreground">{t('package.comment')}</p>
                      <p className="text-sm whitespace-pre-line">{el.comment}</p>
                    </div>
                  ) : null}
                  {snap?.snapshot.valueSchema ? (
                    <div className="space-y-1.5">
                      <p className="text-xs text-muted-foreground">{t('package.values')}</p>
                      <SchemaValueView
                        schema={snap.snapshot.valueSchema}
                        value={el.values as JsonValue | undefined}
                        label={name}
                      />
                    </div>
                  ) : null}
                  <div className="space-y-1.5">
                    <p className="text-xs text-muted-foreground">{t('package.documents')}</p>
                    <DocumentList documents={el.documents} caseId={approvalCase.id} />
                  </div>
                </div>
              </details>
            </li>
          )
        })}
      </ul>
      <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Download aria-hidden="true" className="size-3.5" />
        {t('revisions.immutableHint')}
      </p>
    </div>
  )
}
