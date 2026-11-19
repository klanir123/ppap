import { Loader2, Send, TriangleAlert } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
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

import { useSubmitDraft, type ApprovalCaseDetail, type Submission } from '../api'
import { problemCode, problemMessage } from '../components/problem'
import type { DraftSaver } from './use-draft-saver'

interface SubmitDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  approvalCase: ApprovalCaseDetail
  draft: Submission
  saver: DraftSaver
  onShowIssues: () => void
  /** Called with the immutable revision (hash + signature) after a successful submit. */
  onSubmitted: (submission: Submission) => void
}

/** Confirmation before submitting: summary + explicit confirmation → immutable, signed revision. */
export function SubmitDialog({
  open,
  onOpenChange,
  approvalCase,
  draft,
  saver,
  onShowIssues,
  onSubmitted,
}: SubmitDialogProps) {
  const { t } = useTranslation(['approvals', 'forms'])
  const confirmId = useId()
  const [confirmed, setConfirmed] = useState(false)
  const submit = useSubmitDraft()
  const completeness = draft.completeness
  const incomplete = completeness ? !completeness.complete : false
  const documentCount = draft.elements.reduce((n, e) => n + e.documents.length, 0)
  const assessed = draft.elements.filter((e) => e.selfAssessment).length

  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      setConfirmed(false)
      submit.reset()
    }
  }

  const run = async () => {
    await saver.flush()
    // mutateAsync: the workspace unmounts when the refetched case leaves IN_PROGRESS,
    // per-call mutate callbacks would then be dropped – the parent keeps the result
    const submission = await submit.mutateAsync({ id: approvalCase.id, version: saver.version() }).catch(() => null)
    if (submission) onSubmitted(submission)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-lg">
        <>
          <>
            <DialogHeader>
              <DialogTitle>{t('submit.title', { caseNumber: approvalCase.caseNumber })}</DialogTitle>
              <DialogDescription>{t('submit.description', { revision: draft.revision })}</DialogDescription>
            </DialogHeader>
            <dl className="grid grid-cols-2 gap-3 rounded-lg border p-4 text-sm">
              <div>
                <dt className="text-xs text-muted-foreground">{t('submit.partner')}</dt>
                <dd className="font-medium">{approvalCase.partner.name ?? approvalCase.partner.bpnl}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">{t('submit.part')}</dt>
                <dd className="font-mono text-xs">{approvalCase.part.partNumber}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">{t('submit.assessed')}</dt>
                <dd className="tabular">{t('submit.ofElements', { done: assessed, total: draft.elements.length })}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">{t('submit.documents')}</dt>
                <dd className="tabular">{documentCount}</dd>
              </div>
            </dl>
            {incomplete || problemCode(submit.error) === 'INCOMPLETE_SUBMISSION' ? (
              <Alert variant="destructive">
                <TriangleAlert aria-hidden="true" />
                <AlertTitle>{t('submit.incompleteTitle')}</AlertTitle>
                <AlertDescription>
                  <p>{t('submit.incompleteDescription', { count: completeness?.issues.length ?? 0 })}</p>
                  <Button
                    variant="link"
                    className="h-auto p-0"
                    onClick={() => {
                      close(false)
                      onShowIssues()
                    }}
                  >
                    {t('submit.showIssues')}
                  </Button>
                </AlertDescription>
              </Alert>
            ) : (
              <div className="flex items-start gap-3 rounded-lg border bg-muted/40 p-3">
                <Checkbox
                  id={confirmId}
                  checked={confirmed}
                  onCheckedChange={(c) => setConfirmed(c === true)}
                  className="mt-0.5"
                />
                <Label htmlFor={confirmId} className="text-sm leading-snug font-normal">
                  {t('submit.confirm')}
                </Label>
              </div>
            )}
            {submit.isError && problemCode(submit.error) !== 'INCOMPLETE_SUBMISSION' ? (
              <p role="alert" className="text-sm text-destructive">
                {problemMessage(submit.error)}
              </p>
            ) : null}
            <DialogFooter>
              <Button variant="outline" onClick={() => close(false)}>
                {t('forms:cancel')}
              </Button>
              <Button onClick={() => void run()} disabled={!confirmed || incomplete || submit.isPending}>
                {submit.isPending ? (
                  <Loader2 aria-hidden="true" className="animate-spin" />
                ) : (
                  <Send aria-hidden="true" />
                )}
                {t('submit.submit')}
              </Button>
            </DialogFooter>
          </>
        </>
      </DialogContent>
    </Dialog>
  )
}
