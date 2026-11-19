import { CircleCheck, CircleSlash, CircleX, Info, Loader2, RefreshCw, TriangleAlert } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { SegmentedControl } from '@/components/SegmentedControl'
import { StatusBadge } from '@/components/StatusBadge'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { translateErrorCode } from '@/lib/problem-errors'

import type {
  CompletenessIssue,
  DraftElementInput,
  JsonValue,
  RequestElement,
  SelfAssessment,
  SubmissionElement,
} from '../api'
import { DocumentList, DocumentUpload } from '../components/Documents'
import { EvidenceTypeLabel } from '../components/ElementStateIcon'
import { localized } from '../components/format'
import { hasValues, needsComment, needsDocuments } from '../model/elements'
import { normalizePointer } from '../schema-form/schema'
import { SchemaForm } from '../schema-form/SchemaForm'
import type { DraftSaver, ElementSaveStatus } from './use-draft-saver'

export const AUTOSAVE_DELAY_MS = 800

interface ElementEditorProps {
  request: RequestElement
  element: SubmissionElement | undefined
  issues: CompletenessIssue[]
  saver: DraftSaver
  status: ElementSaveStatus | undefined
}

const inputOf = (e: SubmissionElement | undefined): DraftElementInput => ({
  selfAssessment: e?.selfAssessment ?? null,
  values: e?.values as JsonValue | undefined,
  comment: e?.comment ?? '',
})

/** Editor of one element: self-assessment, comment, structured values, documents; autosaves (debounced). */
export function ElementEditor({ request, element, issues, saver, status }: ElementEditorProps) {
  const { t, i18n } = useTranslation('approvals')
  const commentId = useId()
  const [draft, setDraft] = useState<DraftElementInput>(() => inputOf(element))
  const [dirty, setDirty] = useState(false)
  const [removing, setRemoving] = useState<string>()
  const timer = useRef<number | undefined>(undefined)
  const latest = useRef(draft)
  const pending = useRef(false)
  const { saveElement } = saver
  const code = request.code
  const name = localized(request.snapshot, i18n.resolvedLanguage)
  const evidence = request.snapshot.evidenceType
  const schema = request.snapshot.valueSchema
  const commentRequired = needsComment(draft.selfAssessment)
  const commentMissing = commentRequired && !draft.comment.trim()

  const flush = () => {
    window.clearTimeout(timer.current)
    if (!pending.current) return
    pending.current = false
    void saveElement(code, latest.current).catch(() => undefined)
  }

  // flush pending edits when switching elements / leaving the page
  useEffect(
    () => () => {
      window.clearTimeout(timer.current)
      if (pending.current) void saveElement(code, latest.current).catch(() => undefined)
    },
    [code, saveElement],
  )

  const change = (patch: Partial<DraftElementInput>) => {
    const next = { ...latest.current, ...patch }
    latest.current = next
    setDraft(next)
    setDirty(true)
    pending.current = true
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(flush, AUTOSAVE_DELAY_MS)
  }

  const docs = element?.documents ?? []
  const serverIssues = [...issues.map((i) => i.code), ...(element?.issues ?? []).map((i) => i.code)]
  const uniqueIssues = [...new Set(serverIssues)]
  // draft values that violate the value schema are stored and reported per JSON pointer (Klarstellungen §6)
  const valueIssues = (element?.issues ?? [])
    .filter((i) => i.code === 'VALUE_SCHEMA_VIOLATION')
    .map((i) => ({ pointer: normalizePointer(i.field), code: 'VALUE_SCHEMA_VIOLATION' as const, message: i.message }))
  const valueErrors = status?.state === 'invalid' ? status.valueErrors : valueIssues

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-xs text-muted-foreground">{code}</span>
          <StatusBadge kind="requirement" status={request.requirement} size="sm" />
          <EvidenceTypeLabel type={evidence} />
        </div>
        <h2 className="text-lg font-semibold tracking-tight">{name}</h2>
        {request.note ? (
          <p className="flex gap-2 rounded-md border border-participant/30 bg-participant-soft/50 px-3 py-2 text-sm">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-participant-soft-foreground" />
            <span>
              <span className="font-medium">{t('package.customerNote')}: </span>
              {request.note}
            </span>
          </p>
        ) : null}
      </header>

      <SaveStatusBar
        status={status}
        dirty={dirty}
        onReload={() => {
          setDirty(false)
          void saver.reload(code)
        }}
        onOverwrite={() => void saver.overwrite(code, latest.current).catch(() => undefined)}
      />

      {uniqueIssues.length > 0 ? (
        <ul className="space-y-1" aria-label={t('package.issues')}>
          {uniqueIssues.map((c) => (
            <li key={c} className="flex items-center gap-2 text-xs text-(--status-warning-fg)">
              <TriangleAlert aria-hidden="true" className="size-3.5" />
              {t(`completeness.issues.${c}` as 'completeness.issues.MISSING_SELF_ASSESSMENT', { defaultValue: c })}
            </li>
          ))}
        </ul>
      ) : null}

      <section className="space-y-2" aria-labelledby={`${commentId}-assessment`}>
        <h3 id={`${commentId}-assessment`} className="flex items-center gap-1 text-sm font-medium">
          {t('package.selfAssessment')}
          {request.requirement === 'REQUIRED' ? (
            <span aria-hidden="true" className="text-destructive">
              *
            </span>
          ) : null}
        </h3>
        <SegmentedControl<SelfAssessment>
          label={t('package.selfAssessment')}
          value={draft.selfAssessment}
          onValueChange={(selfAssessment) => change({ selfAssessment })}
          options={[
            { value: 'OK', label: t('package.assessment.OK'), icon: CircleCheck, token: 'ok' },
            { value: 'NOK', label: t('package.assessment.NOK'), icon: CircleX, token: 'nok' },
            { value: 'NA', label: t('package.assessment.NA'), icon: CircleSlash, token: 'na' },
          ]}
        />
      </section>

      <div className="grid gap-2">
        <Label htmlFor={commentId} className="gap-1">
          {t('package.comment')}
          {commentRequired ? (
            <>
              <span aria-hidden="true" className="text-destructive">
                *
              </span>
              <span className="sr-only">({t('package.commentRequiredShort')})</span>
            </>
          ) : null}
        </Label>
        <Textarea
          id={commentId}
          rows={3}
          maxLength={4000}
          value={draft.comment}
          aria-invalid={commentMissing || undefined}
          aria-describedby={commentMissing ? `${commentId}-error` : undefined}
          placeholder={commentRequired ? t('package.commentPlaceholderRequired') : t('package.commentPlaceholder')}
          onChange={(e) => change({ comment: e.target.value })}
          onBlur={flush}
        />
        {commentMissing ? (
          <p id={`${commentId}-error`} className="text-xs font-medium text-destructive">
            {t('package.commentRequired')}
          </p>
        ) : null}
      </div>

      {hasValues(evidence) && schema ? (
        <section className="space-y-3" aria-labelledby={`${commentId}-values`}>
          <h3 id={`${commentId}-values`} className="text-sm font-medium">
            {t('package.values')}
          </h3>
          {draft.selfAssessment === 'NA' ? (
            <p className="text-sm text-muted-foreground">{t('package.valuesNotNeeded')}</p>
          ) : (
            <SchemaForm
              schema={schema}
              value={draft.values as JsonValue | undefined}
              onChange={(values) => change({ values })}
              serverErrors={valueErrors}
              label={name}
            />
          )}
        </section>
      ) : null}

      <section className="space-y-3" aria-labelledby={`${commentId}-docs`}>
        <h3 id={`${commentId}-docs`} className="flex items-center gap-1 text-sm font-medium">
          {t('package.documents')}
          {needsDocuments(evidence) && draft.selfAssessment !== 'NA' ? (
            <span className="text-xs font-normal text-muted-foreground">({t('package.documentsRequired')})</span>
          ) : null}
        </h3>
        <DocumentList
          documents={docs}
          removingId={removing}
          onRemove={(doc) => {
            if (!doc.id) return
            setRemoving(doc.id)
            void saver
              .removeDocument(code, doc.id)
              .catch(() => undefined)
              .finally(() => setRemoving(undefined))
          }}
        />
        <DocumentUpload compact onUploaded={(doc) => saver.addDocument(code, doc.id)} />
      </section>
    </div>
  )
}

function SaveStatusBar({
  status,
  dirty,
  onReload,
  onOverwrite,
}: {
  status: ElementSaveStatus | undefined
  dirty: boolean
  onReload: () => void
  onOverwrite: () => void
}) {
  const { t } = useTranslation('approvals')
  const state = status?.state ?? 'idle'
  if (state === 'conflict') {
    return (
      <Alert variant="destructive">
        <TriangleAlert aria-hidden="true" />
        <AlertTitle>{t('package.conflictTitle')}</AlertTitle>
        <AlertDescription>
          <p>{t('package.conflictDescription')}</p>
          <div className="mt-2 flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={onReload}>
              <RefreshCw aria-hidden="true" />
              {t('package.conflictReload')}
            </Button>
            <Button size="sm" onClick={onOverwrite}>
              {t('package.conflictOverwrite')}
            </Button>
          </div>
        </AlertDescription>
      </Alert>
    )
  }
  if (state === 'invalid') {
    return (
      <p role="alert" className="flex items-center gap-2 text-sm text-destructive">
        <TriangleAlert aria-hidden="true" className="size-4" />
        {t('package.saveInvalid')}
      </p>
    )
  }
  if (state === 'error') {
    return (
      <p role="alert" className="flex items-center gap-2 text-sm text-destructive">
        <TriangleAlert aria-hidden="true" className="size-4" />
        {t('package.saveFailed', { message: translateErrorCode(status?.message) })}
      </p>
    )
  }
  return (
    <p className="flex h-5 items-center gap-1.5 text-xs text-muted-foreground" aria-live="polite">
      {state === 'saving' ? (
        <>
          <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
          {t('package.saving')}
        </>
      ) : state === 'saved' ? (
        <>
          <CircleCheck aria-hidden="true" className="size-3.5 text-(--status-ok-solid)" />
          {t('package.saved')}
        </>
      ) : dirty ? (
        t('package.unsaved')
      ) : (
        t('package.autosaveHint')
      )}
    </p>
  )
}
