import { getRouteApi, Link } from '@tanstack/react-router'
import { ArrowLeft, FileSpreadsheet, Loader2, Lock, Send } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useCurrentUser } from '@/features/auth/current-user'
import { cn } from '@/lib/utils'

import { useCase, useSubmission, type ApprovalCaseDetail, type Submission } from '../api'
import { CaseStatusBadge, DueDate } from '../components/badges'
import { ElementStateIcon } from '../components/ElementStateIcon'
import { localized } from '../components/format'
import { availableWorkspace, permittedActions } from '../model/actions'
import { elementState, issuesByElement, type ElementState } from '../model/elements'
import { CompletenessPanel } from './CompletenessPanel'
import { ElementEditor } from './ElementEditor'
import { ExcelImportDialog } from './ExcelImportDialog'
import { SubmitDialog } from './SubmitDialog'
import { useDraftSaver } from './use-draft-saver'

const routeApi = getRouteApi('/approvals/$caseId/package')

/** Workspace "PPF-Paket erstellen" (UC-05): element list + editor, completeness, Excel import, submit. */
export function PackageWorkspace() {
  const { t } = useTranslation('approvals')
  const { caseId } = routeApi.useParams()
  const me = useCurrentUser()
  const caseQuery = useCase(caseId)
  const draftQuery = useSubmission(caseId, 'draft')

  if (caseQuery.isPending || draftQuery.isPending) return <WorkspaceSkeleton />
  const c = caseQuery.data
  const editable = c && availableWorkspace(c, permittedActions(c.allowedActions, me.roles)) === 'package'
  if (!c || !editable || !draftQuery.data) {
    return (
      <EmptyState
        icon={Lock}
        title={t('package.notEditable')}
        description={t('package.notEditableDescription')}
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
  return <Workspace approvalCase={c} draft={draftQuery.data} />
}

function Workspace({ approvalCase: c, draft }: { approvalCase: ApprovalCaseDetail; draft: Submission }) {
  const { t, i18n } = useTranslation('approvals')
  const search = routeApi.useSearch()
  const navigate = routeApi.useNavigate()
  const saver = useDraftSaver(c.id, c.version)
  const [excelOpen, setExcelOpen] = useState(false)
  const [submitOpen, setSubmitOpen] = useState(false)
  const completenessRef = useRef<HTMLDivElement>(null)
  const editorRef = useRef<HTMLDivElement>(null)
  const lng = i18n.resolvedLanguage

  const elements = c.request.elements
  const names = new Map(elements.map((e) => [e.code, localized(e.snapshot, lng)]))
  const byCode = new Map(draft.elements.map((e) => [e.code, e]))
  const issues = issuesByElement(draft.completeness)
  const selectedCode = elements.some((e) => e.code === search.element) ? search.element! : elements[0]?.code
  const selected = elements.find((e) => e.code === selectedCode)

  const select = (code: string) => {
    void navigate({ search: { element: code }, replace: true })
    // move focus to the editor for keyboard users
    window.setTimeout(() => editorRef.current?.focus(), 0)
  }

  const required = elements.filter((e) => e.requirement === 'REQUIRED')
  const optional = elements.filter((e) => e.requirement !== 'REQUIRED')

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="space-y-2">
          <Button variant="ghost" size="sm" className="-ml-2" asChild>
            <Link to="/approvals/$caseId" params={{ caseId: c.id }}>
              <ArrowLeft aria-hidden="true" />
              {c.caseNumber}
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">{t('package.title')}</h1>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <CaseStatusBadge status={c.status} size="sm" />
            <span>{t('revisions.revisionTitle', { revision: draft.revision })}</span>
            <span>
              <span className="font-mono">{c.part.partNumber}</span> · {c.partner.name ?? c.partner.bpnl}
            </span>
            <DueDate dueDate={c.dueDate} overdue={c.overdue} status={c.status} />
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setExcelOpen(true)}>
            <FileSpreadsheet aria-hidden="true" />
            {t('package.excelImport')}
          </Button>
          <Button onClick={() => setSubmitOpen(true)} disabled={saver.busy}>
            {saver.busy ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Send aria-hidden="true" />}
            {t('package.submit')}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[19rem_minmax(0,1fr)]">
        <div className="space-y-4">
          <div ref={completenessRef} tabIndex={-1} className="outline-none">
            <CompletenessPanel completeness={draft.completeness} names={names} onSelect={select} />
          </div>
          <nav aria-label={t('package.elementList')} className="rounded-lg border bg-card p-2">
            <h2 className="px-2.5 pt-1 pb-1.5 text-xs font-medium text-muted-foreground">
              {t('package.requiredElements', { count: required.length })}
            </h2>
            <ul className="max-h-[40svh] space-y-0.5 relative overflow-y-auto lg:max-h-none">
              {required.map((e) => (
                <li key={e.code}>
                  <ElementButton
                    code={e.code}
                    name={names.get(e.code) ?? e.code}
                    state={elementState(e.requirement, byCode.get(e.code), issues.get(e.code)?.length ?? 0)}
                    active={e.code === selectedCode}
                    saving={saver.status[e.code]?.state === 'saving'}
                    onSelect={select}
                  />
                </li>
              ))}
            </ul>
            {optional.length > 0 ? (
              <>
                <h2 className="mt-2 border-t px-2.5 pt-3 pb-1.5 text-xs font-medium text-muted-foreground">
                  {t('package.optionalElements', { count: optional.length })}
                </h2>
                <ul className="space-y-0.5">
                  {optional.map((e) => (
                    <li key={e.code}>
                      <ElementButton
                        code={e.code}
                        name={names.get(e.code) ?? e.code}
                        state={elementState(e.requirement, byCode.get(e.code), issues.get(e.code)?.length ?? 0)}
                        active={e.code === selectedCode}
                        saving={saver.status[e.code]?.state === 'saving'}
                        onSelect={select}
                      />
                    </li>
                  ))}
                </ul>
              </>
            ) : null}
          </nav>
        </div>

        <Card className="min-w-0">
          <CardContent ref={editorRef} tabIndex={-1} className="outline-none" aria-label={t('package.editor')}>
            {selected ? (
              <ElementEditor
                key={`${selected.code}:${draft.revision}:${saver.epoch}`}
                request={selected}
                element={byCode.get(selected.code)}
                issues={issues.get(selected.code) ?? []}
                saver={saver}
                status={saver.status[selected.code]}
              />
            ) : null}
          </CardContent>
        </Card>
      </div>

      <ExcelImportDialog open={excelOpen} onOpenChange={setExcelOpen} caseId={c.id} saver={saver} names={names} />
      <SubmitDialog
        open={submitOpen}
        onOpenChange={setSubmitOpen}
        approvalCase={c}
        draft={draft}
        saver={saver}
        onShowIssues={() => completenessRef.current?.focus()}
      />
    </div>
  )
}

function ElementButton({
  code,
  name,
  state,
  active,
  saving,
  onSelect,
}: {
  code: string
  name: string
  state: ElementState
  active: boolean
  saving: boolean
  onSelect: (code: string) => void
}) {
  return (
    <button
      type="button"
      onClick={() => onSelect(code)}
      aria-current={active ? 'true' : undefined}
      className={cn(
        'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left transition-colors hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring',
        active && 'bg-participant-soft text-participant-soft-foreground hover:bg-participant-soft',
      )}
    >
      <ElementStateIcon state={state} />
      <span className="min-w-0 flex-1">
        <span className="block font-mono text-[11px] text-muted-foreground">{code}</span>
        <span className="block truncate text-sm">{name}</span>
      </span>
      {saving ? <Loader2 aria-hidden="true" className="size-3.5 animate-spin text-muted-foreground" /> : null}
    </button>
  )
}

function WorkspaceSkeleton() {
  return (
    <div className="space-y-5" aria-busy="true">
      <Skeleton className="h-16 w-96 max-w-full" />
      <div className="grid gap-5 lg:grid-cols-[19rem_1fr]">
        <Skeleton className="h-[28rem] w-full" />
        <Skeleton className="h-[28rem] w-full" />
      </div>
    </div>
  )
}
