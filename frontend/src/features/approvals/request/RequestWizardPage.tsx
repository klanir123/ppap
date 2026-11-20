import { getRouteApi, Link, useNavigate } from '@tanstack/react-router'
import { ArrowLeft, ArrowRight, GitBranchPlus, Loader2, Save, Send, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { PageHeader } from '@/components/PageHeader'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

import {
  approvalsApi,
  useCase,
  useCatalogue,
  useSupplierPartOptions,
  type ApprovalCaseDetail,
  type CasePartnerRef,
} from '../api'
import { problemMessage } from '../components/problem'
import { Stepper } from '../components/Stepper'
import { todayIso } from '../model/status'
import {
  elementInputs,
  emptyRequest,
  subRequestDefaults,
  firstInvalidStep,
  fromDraft,
  prefillElements,
  REQUEST_STEPS,
  stepErrors,
  toCreateInput,
  type RequestStep,
  type RequestValues,
} from './request-model'
import { ElementsStep, PartStep, ReasonStep, ScheduleStep, SummaryStep, SupplierStep } from './RequestSteps'

const routeApi = getRouteApi('/approvals/new')

/** Wizard "Vorlage anfordern" (UC-03): create or continue a draft, then save or request. */
export function RequestWizardPage() {
  const { caseId, parentId: parentParam } = routeApi.useSearch()
  const draft = useCase(caseId)
  const catalogue = useCatalogue()
  // a sub-supplier request (UC-09) – new (parentId) or the draft of an existing child case
  const parentId = caseId ? (draft.data?.parentCase?.id ?? undefined) : parentParam
  const parent = useCase(parentParam && !caseId ? parentParam : undefined)
  if ((caseId && (draft.isPending || catalogue.isPending)) || (parentParam && !caseId && parent.isPending)) {
    return (
      <div className="space-y-6" aria-busy="true">
        <Skeleton className="h-8 w-72" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }
  const initial =
    caseId && draft.data
      ? fromDraft(
          draft.data,
          catalogue.elements,
          catalogue.levels.find((l) => l.code === draft.data.level.code),
        )
      : parent.data
        ? subRequestDefaults(parent.data, todayIso())
        : emptyRequest()
  return (
    <RequestWizard
      key={caseId ?? parentId ?? 'new'}
      initial={initial}
      draft={draft.data}
      parent={parent.data ?? draft.data?.parentCase ?? undefined}
      parentId={parentId}
    />
  )
}

type ParentInfo = Pick<ApprovalCaseDetail, 'id' | 'caseNumber'> & { partner: CasePartnerRef }

function RequestWizard({
  initial,
  draft,
  parent,
  parentId,
}: {
  initial: RequestValues
  draft?: ApprovalCaseDetail
  parent?: ParentInfo
  parentId?: string
}) {
  const { t } = useTranslation('approvals')
  const navigate = useNavigate()
  const catalogue = useCatalogue()
  const [values, setValues] = useState<RequestValues>(initial)
  const [stepIndex, setStepIndex] = useState(0)
  const [maxReached, setMaxReached] = useState(draft ? REQUEST_STEPS.length - 1 : 0)
  const [showErrors, setShowErrors] = useState(false)
  const [busy, setBusy] = useState<'draft' | 'send' | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const options = useSupplierPartOptions('SUPPLIER', undefined)
  const step = REQUEST_STEPS[stepIndex]!
  const today = todayIso()
  const errors = showErrors ? stepErrors(step, values, today) : {}

  const partOption = options.data?.find((o) => o.partId === values.partId)
  const partner = partOption?.partners.find((p) => p.relationId === values.relationId)
  const exchangeReady = partner?.exchangeReady ?? false

  const patch = (next: Partial<RequestValues>) => setValues((prev) => ({ ...prev, ...next }))

  // focus the step heading after navigation (screen readers announce the new step)
  useEffect(() => {
    headingRef.current?.focus()
  }, [stepIndex])

  const goTo = (index: number) => {
    const target = REQUEST_STEPS[index]!
    // entering the element step prefills from the level matrix (again, if the level changed)
    if (target === 'elements' && values.elementsLevel !== values.levelCode) {
      const level = catalogue.levels.find((l) => l.code === values.levelCode)
      patch({ elements: prefillElements(level, catalogue.elements, values.elements), elementsLevel: values.levelCode })
    }
    setShowErrors(false)
    setStepIndex(index)
    setMaxReached((m) => Math.max(m, index))
  }

  const next = () => {
    if (Object.keys(stepErrors(step, values, today)).length > 0) {
      setShowErrors(true)
      return
    }
    goTo(stepIndex + 1)
  }

  async function save(send: boolean) {
    const invalid = firstInvalidStep(values, 'summary', today)
    if (invalid) {
      goTo(REQUEST_STEPS.indexOf(invalid))
      setShowErrors(true)
      return
    }
    setBusy(send ? 'send' : 'draft')
    setSaveError(null)
    let id = draft?.id
    try {
      let version = draft?.version
      if (draft) {
        const { elements: _elements, ...fields } = toCreateInput(values)
        const updated = await approvalsApi.updateCase(draft.id, fields, version)
        version = updated.version ?? updated.data?.version ?? version
        const withElements = await approvalsApi.updateRequestElements(draft.id, elementInputs(values), version)
        version = withElements.version ?? withElements.data?.version ?? version
      } else if (parentId) {
        const created = await approvalsApi.createSubRequest(parentId, toCreateInput(values))
        id = created.id
        version = created.version
      } else {
        const created = await approvalsApi.createCase(toCreateInput(values))
        id = created.id
        version = created.version
      }
      // attachments: add new, remove dropped (draft only)
      const before = new Set((draft?.request.attachments ?? []).map((d) => d.id))
      const after = new Set(values.attachments.map((d) => d.id))
      for (const doc of values.attachments.filter((d) => d.id && !before.has(d.id))) {
        const res = await approvalsApi.addRequestAttachment(id!, doc.id!, version)
        version = res.version ?? res.data?.version ?? version
      }
      for (const docId of [...before].filter((d) => d && !after.has(d))) {
        const res = await approvalsApi.removeRequestAttachment(id!, docId!, version)
        version = res.version ?? version
      }
      if (send) {
        if (version === undefined) version = (await approvalsApi.getCase(id!)).version
        await approvalsApi.transitionCase(id!, 'send', version)
        toast.success(t('wizard.sent'))
      } else {
        toast.success(t('wizard.savedDraft'))
      }
      void navigate({ to: '/approvals/$caseId', params: { caseId: id! } })
    } catch (error) {
      if (id && !draft) {
        // created, but a later step failed: continue on the draft
        toast.error(t('wizard.partialFailure'), { description: problemMessage(error) })
        void navigate({ to: '/approvals/$caseId', params: { caseId: id } })
      } else {
        setSaveError(problemMessage(error))
      }
    } finally {
      setBusy(null)
    }
  }

  const steps = REQUEST_STEPS.map((id) => ({ id, label: t(`wizard.steps.${id}`) }))
  const stepProps = { values, patch, errors, options, catalogue }

  return (
    <div className="space-y-6">
      <Button variant="ghost" size="sm" className="-ml-2" asChild>
        {draft ? (
          <Link to="/approvals/$caseId" params={{ caseId: draft.id }}>
            <ArrowLeft aria-hidden="true" />
            {draft.caseNumber}
          </Link>
        ) : parent ? (
          <Link to="/approvals/$caseId" params={{ caseId: parent.id }}>
            <ArrowLeft aria-hidden="true" />
            {parent.caseNumber}
          </Link>
        ) : (
          <Link to="/approvals">
            <ArrowLeft aria-hidden="true" />
            {t('list.title')}
          </Link>
        )}
      </Button>
      <PageHeader
        title={
          draft
            ? t('wizard.editTitle', { caseNumber: draft.caseNumber })
            : parent
              ? t('cascade.request.title')
              : t('wizard.title')
        }
        description={parent && !draft ? t('cascade.request.description') : t('wizard.description')}
      />
      {parent ? (
        <Alert data-testid="sub-request-parent">
          <GitBranchPlus aria-hidden="true" />
          <AlertTitle>
            {t('cascade.request.parentTitle', {
              caseNumber: parent.caseNumber,
              partner: parent.partner.name ?? parent.partner.bpnl,
            })}
          </AlertTitle>
          <AlertDescription>{t('cascade.request.parentHint')}</AlertDescription>
        </Alert>
      ) : null}
      <Stepper steps={steps} current={stepIndex} maxReached={maxReached} onSelect={goTo} />

      <Card>
        <CardContent className="space-y-6">
          <div className="space-y-1">
            <h2 ref={headingRef} tabIndex={-1} className="text-lg font-semibold tracking-tight outline-none">
              {t(`wizard.steps.${step}`)}
            </h2>
            <p className="text-sm text-muted-foreground">{t(`wizard.hints.${step}`)}</p>
          </div>
          <StepBody step={step} {...stepProps} exchangeReady={exchangeReady} onEdit={goTo} />
          {saveError ? (
            <Alert variant="destructive">
              <TriangleAlert aria-hidden="true" />
              <AlertTitle>{t('wizard.saveFailed')}</AlertTitle>
              <AlertDescription>{saveError}</AlertDescription>
            </Alert>
          ) : null}
        </CardContent>
      </Card>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-between">
        <Button variant="outline" onClick={() => goTo(stepIndex - 1)} disabled={stepIndex === 0 || busy !== null}>
          <ArrowLeft aria-hidden="true" />
          {t('wizard.back')}
        </Button>
        {step === 'summary' ? (
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <Button variant="outline" onClick={() => void save(false)} disabled={busy !== null}>
              {busy === 'draft' ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Save aria-hidden="true" />}
              {t('wizard.saveDraft')}
            </Button>
            <Button
              onClick={() => void save(true)}
              disabled={busy !== null || !exchangeReady}
              aria-describedby={!exchangeReady ? 'send-blocked-hint' : undefined}
            >
              {busy === 'send' ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Send aria-hidden="true" />}
              {t('wizard.send')}
            </Button>
          </div>
        ) : (
          <Button onClick={next}>
            {t('wizard.next')}
            <ArrowRight aria-hidden="true" data-icon="inline-end" />
          </Button>
        )}
      </div>
    </div>
  )
}

function StepBody({
  step,
  exchangeReady,
  onEdit,
  ...props
}: React.ComponentProps<typeof PartStep> & {
  step: RequestStep
  exchangeReady: boolean
  onEdit: (index: number) => void
}) {
  switch (step) {
    case 'part':
      return <PartStep {...props} />
    case 'supplier':
      return <SupplierStep {...props} />
    case 'reason':
      return <ReasonStep {...props} />
    case 'elements':
      return <ElementsStep {...props} />
    case 'schedule':
      return <ScheduleStep {...props} />
    case 'summary':
      return <SummaryStep {...props} exchangeReady={exchangeReady} onEdit={onEdit} />
  }
}
