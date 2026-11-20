import { Link } from '@tanstack/react-router'
import { CircleAlert, Info, Loader2, Network, ShieldCheck } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Checkbox } from '@/components/ui/checkbox'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

import { useSubSupplierCandidates, type ApprovalCaseDetail, type Submission, type SubSupplierCandidate } from '../api'
import { CaseStatusBadge, HashText } from '../components/badges'
import { useFormat } from '../components/format'
import { toastProblem } from '../components/problem'
import { SUB_SUPPLIER_REFERENCES, type DraftSaver } from '../package/use-draft-saver'
import {
  blockingChildren,
  candidateResult,
  hasSubSupplierPending,
  isReferenceable,
  referencedCandidateIds,
} from './cascade-model'

/**
 * Package workspace › "Unterlieferanten-Freigaben" (UC-09): pick decided child cases whose decision is
 * referenced in the own submission (metadata only – sub-supplier documents are never passed on).
 */
export function SubSupplierReferencesSection({
  approvalCase: c,
  draft,
  saver,
}: {
  approvalCase: ApprovalCaseDetail
  draft: Submission
  saver: Pick<DraftSaver, 'saveSubSupplierReferences' | 'status'>
}) {
  const { t } = useTranslation('approvals')
  const headingId = useId()
  const hasChildren = (c.childCases?.length ?? 0) > 0
  const candidates = useSubSupplierCandidates(c.id, hasChildren)
  const list = candidates.data ?? []
  const serverSelection = referencedCandidateIds(draft.subSupplierReferences, list)
  const [selection, setSelection] = useState<string[] | null>(null)
  const selected = selection ?? serverSelection
  const pending = hasSubSupplierPending(draft.completeness) || blockingChildren(c.childCases).length > 0
  const saving = saver.status[SUB_SUPPLIER_REFERENCES]?.state === 'saving'

  if (!hasChildren) return null

  const toggle = (id: string, checked: boolean) => {
    const next = checked ? [...new Set([...selected, id])] : selected.filter((x) => x !== id)
    setSelection(next)
    saver.saveSubSupplierReferences(next).catch((error: unknown) => {
      setSelection(null)
      toastProblem(error, t('cascade.references.saveFailed'))
    })
  }

  return (
    <section
      aria-labelledby={headingId}
      className="space-y-4 rounded-lg border bg-card p-4"
      data-testid="sub-supplier-references"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="space-y-1">
          <h2 id={headingId} className="flex items-center gap-2 text-sm font-semibold">
            <Network aria-hidden="true" className="size-4 text-muted-foreground" />
            {t('cascade.references.title')}
          </h2>
          <p className="max-w-2xl text-xs text-muted-foreground">{t('cascade.references.description')}</p>
        </div>
        <span className="flex items-center gap-1.5 text-xs text-muted-foreground" aria-live="polite">
          {saving ? <Loader2 aria-hidden="true" className="size-3.5 animate-spin" /> : null}
          {saving ? t('cascade.references.saving') : t('cascade.references.selected', { count: selected.length })}
        </span>
      </div>

      {pending ? (
        <Alert data-testid="sub-supplier-pending">
          <CircleAlert aria-hidden="true" className="text-(--status-warning-solid)" />
          <AlertTitle>{t('cascade.references.pendingTitle')}</AlertTitle>
          <AlertDescription>{t('cascade.references.pendingDescription')}</AlertDescription>
        </Alert>
      ) : null}

      {candidates.isPending ? (
        <div className="space-y-2" aria-busy="true">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : candidates.isError ? (
        <p className="text-sm text-destructive">{t('cascade.references.loadError')}</p>
      ) : list.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-sm text-muted-foreground">
          {t('cascade.references.noCandidates')}
        </p>
      ) : (
        <ul className="grid gap-2 lg:grid-cols-2" aria-label={t('cascade.references.listLabel')}>
          {list.map((candidate) => (
            <li key={candidate.caseId}>
              <CandidateRow
                candidate={candidate}
                checked={selected.includes(candidate.caseId)}
                disabled={saving}
                onCheckedChange={(checked) => toggle(candidate.caseId, checked)}
              />
            </li>
          ))}
        </ul>
      )}

      <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
        <ShieldCheck aria-hidden="true" className="mt-px size-3.5 shrink-0" />
        {t('cascade.references.noDocuments')}
      </p>
    </section>
  )
}

function CandidateRow({
  candidate: c,
  checked,
  disabled,
  onCheckedChange,
}: {
  candidate: SubSupplierCandidate
  checked: boolean
  disabled: boolean
  onCheckedChange: (checked: boolean) => void
}) {
  const { t } = useTranslation('approvals')
  const fmt = useFormat()
  const id = useId()
  const referenceable = isReferenceable(c)
  const result = candidateResult(c)
  return (
    <div
      className={cn(
        'flex h-full items-start gap-3 rounded-lg border px-3 py-2.5 transition-colors',
        checked && 'border-participant/50 bg-participant-soft/50',
        !referenceable && 'bg-muted/30',
      )}
      data-testid="sub-supplier-candidate"
    >
      <Checkbox
        id={id}
        checked={checked}
        disabled={disabled || !referenceable}
        onCheckedChange={(v) => onCheckedChange(v === true)}
        aria-describedby={`${id}-meta`}
        className="mt-0.5"
      />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <label htmlFor={id} className="text-sm font-medium">
            <span className="font-mono text-xs">{c.partNumber ?? '–'}</span>
            {c.partName ? <span className="ml-2">{c.partName}</span> : null}
          </label>
          {result ? <CaseStatusBadge status={result} size="sm" /> : null}
        </div>
        <p id={`${id}-meta`} className="text-xs text-muted-foreground">
          {[
            c.subSupplier.name ?? c.subSupplier.bpnl,
            c.caseNumber,
            c.revision ? t('revisions.revisionTitle', { revision: c.revision }) : undefined,
            c.decidedAt ? fmt.date(c.decidedAt) : undefined,
          ]
            .filter(Boolean)
            .join(' · ')}
        </p>
        <div className="flex flex-wrap items-center gap-2">
          {c.decisionHash ? <HashText hash={c.decisionHash} label={t('cascade.references.copyHash')} /> : null}
          <Link
            to="/approvals/$caseId"
            params={{ caseId: c.caseId }}
            className="rounded-sm text-xs underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
          >
            {t('cascade.references.openCase')}
          </Link>
        </div>
        {!referenceable ? (
          <p className="flex items-center gap-1 text-[11px] text-muted-foreground">
            <Info aria-hidden="true" className="size-3" />
            {t('cascade.references.notReferenceable')}
          </p>
        ) : null}
      </div>
    </div>
  )
}
