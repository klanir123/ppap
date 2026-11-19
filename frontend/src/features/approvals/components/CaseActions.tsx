import { useNavigate } from '@tanstack/react-router'
import { Ban, ClipboardCheck, FilePlus2, Loader2, PackageOpen, Pencil, Play, Send, type LucideIcon } from 'lucide-react'
import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useCurrentUser } from '@/features/auth/current-user'

import { useCancelCase, useNewRevision, useTransition, type ApprovalCase, type CaseAction } from '../api'
import { availableWorkspace, permittedActions } from '../model/actions'
import { problemMessage, toastProblem } from './problem'

interface ActionButton {
  key: string
  label: string
  icon: LucideIcon
  onClick: () => void
  busy?: boolean
  destructive?: boolean
}

/**
 * Primary actions of a case – only those the server allows (`allowedActions`) and the user's roles cover.
 * The first entry is rendered as primary button.
 */
export function CaseActions({ approvalCase }: { approvalCase: ApprovalCase }) {
  const { t } = useTranslation('approvals')
  const me = useCurrentUser()
  const navigate = useNavigate()
  const transition = useTransition()
  const newRevision = useNewRevision()
  const [cancelOpen, setCancelOpen] = useState(false)
  const actions = permittedActions(approvalCase.allowedActions, me.roles)
  const workspace = availableWorkspace(approvalCase, actions)
  const { id, version, caseNumber } = approvalCase

  const goPackage = () => void navigate({ to: '/approvals/$caseId/package', params: { caseId: id } })
  const goReview = () => void navigate({ to: '/approvals/$caseId/review', params: { caseId: id } })

  const run = (action: 'send' | 'start-work' | 'start-review', success: string, then?: () => void) =>
    transition.mutate(
      { id, action, version },
      {
        onSuccess: () => {
          toast.success(success)
          then?.()
        },
        onError: (error) => toastProblem(error, t('actions.failed')),
      },
    )

  const buttons: ActionButton[] = []
  const has = (a: CaseAction) => actions.includes(a)

  if (has('START_WORK'))
    buttons.push({
      key: 'start-work',
      label: t('actions.startWork'),
      icon: Play,
      busy: transition.isPending && transition.variables?.action === 'start-work',
      onClick: () => run('start-work', t('actions.startWorkDone', { caseNumber }), goPackage),
    })
  if (workspace === 'package')
    buttons.push({ key: 'package', label: t('actions.openPackage'), icon: PackageOpen, onClick: goPackage })
  if (has('START_REVIEW'))
    buttons.push({
      key: 'start-review',
      label: t('actions.startReview'),
      icon: Play,
      busy: transition.isPending && transition.variables?.action === 'start-review',
      onClick: () => run('start-review', t('actions.startReviewDone', { caseNumber }), goReview),
    })
  else if (workspace === 'review')
    buttons.push({ key: 'review', label: t('actions.openReview'), icon: ClipboardCheck, onClick: goReview })
  if (has('SEND'))
    buttons.push({
      key: 'send',
      label: t('actions.send'),
      icon: Send,
      busy: transition.isPending && transition.variables?.action === 'send',
      onClick: () => run('send', t('actions.sendDone', { caseNumber })),
    })
  if (has('EDIT'))
    buttons.push({
      key: 'edit',
      label: t('actions.editDraft'),
      icon: Pencil,
      onClick: () => void navigate({ to: '/approvals/new', search: { caseId: id } }),
    })
  if (has('NEW_REVISION'))
    buttons.push({
      key: 'new-revision',
      label: t('actions.newRevision'),
      icon: FilePlus2,
      busy: newRevision.isPending,
      onClick: () =>
        newRevision.mutate(
          { id, version },
          {
            onSuccess: (submission) => {
              toast.success(t('actions.newRevisionDone', { revision: submission.revision }))
              goPackage()
            },
            onError: (error) => toastProblem(error, t('actions.failed')),
          },
        ),
    })
  if (has('CANCEL'))
    buttons.push({
      key: 'cancel',
      label: t('actions.cancel'),
      icon: Ban,
      destructive: true,
      onClick: () => setCancelOpen(true),
    })

  if (buttons.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label={t('actions.label')}>
      {buttons.map((b, index) => (
        <Button
          key={b.key}
          variant={index === 0 && !b.destructive ? 'default' : b.destructive ? 'ghost' : 'outline'}
          className={b.destructive ? 'text-destructive hover:text-destructive' : undefined}
          onClick={b.onClick}
          disabled={b.busy}
          data-action={b.key}
        >
          {b.busy ? <Loader2 aria-hidden="true" className="animate-spin" /> : <b.icon aria-hidden="true" />}
          {b.label}
        </Button>
      ))}
      <CancelDialog approvalCase={approvalCase} open={cancelOpen} onOpenChange={setCancelOpen} />
    </div>
  )
}

/** Cancel with mandatory reason (DRAFT/REQUESTED → CANCELLED). */
export function CancelDialog({
  approvalCase,
  open,
  onOpenChange,
}: {
  approvalCase: ApprovalCase
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation(['approvals', 'forms'])
  const reasonId = useId()
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const cancel = useCancelCase()
  const missing = !reason.trim()

  const close = (next: boolean) => {
    onOpenChange(next)
    if (!next) {
      setReason('')
      setTouched(false)
      cancel.reset()
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent>
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            setTouched(true)
            if (missing) return
            cancel.mutate(
              { id: approvalCase.id, reason: reason.trim(), version: approvalCase.version },
              {
                onSuccess: () => {
                  toast.success(t('cancelDialog.done', { caseNumber: approvalCase.caseNumber }))
                  close(false)
                },
              },
            )
          }}
        >
          <DialogHeader>
            <DialogTitle>{t('cancelDialog.title', { caseNumber: approvalCase.caseNumber })}</DialogTitle>
            <DialogDescription>{t('cancelDialog.description')}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor={reasonId} className="gap-1">
              {t('cancelDialog.reason')}
              <span aria-hidden="true" className="text-destructive">
                *
              </span>
            </Label>
            <Textarea
              id={reasonId}
              value={reason}
              rows={3}
              aria-required="true"
              aria-invalid={touched && missing ? true : undefined}
              aria-describedby={touched && missing ? `${reasonId}-error` : undefined}
              onChange={(e) => setReason(e.target.value)}
            />
            {touched && missing ? (
              <p id={`${reasonId}-error`} className="text-xs font-medium text-destructive">
                {t('cancelDialog.reasonRequired')}
              </p>
            ) : null}
          </div>
          {cancel.isError ? (
            <p role="alert" className="text-sm text-destructive">
              {problemMessage(cancel.error)}
            </p>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              {t('forms:cancel')}
            </Button>
            <Button type="submit" variant="destructive" disabled={cancel.isPending}>
              {cancel.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <Ban aria-hidden="true" />}
              {t('cancelDialog.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
