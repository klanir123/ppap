import { Link2, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { usePermission } from '@/features/auth/current-user'
import type { ApprovalCase } from '@/features/approvals/api/types'
import { statusStyle } from '@/lib/status-style'

import { caseClarification } from '../api/client'
import { AssignPartDialog } from './AssignPartDialog'

/** UC-04 A1: received request whose customer part number is not mapped – warning + "Teil zuordnen". */
export function ClarificationBanner({ approvalCase: c }: { approvalCase: ApprovalCase }) {
  const { t } = useTranslation('exchange')
  const canAssign = usePermission('approvals:supply')
  const [open, setOpen] = useState(false)
  const clarification = caseClarification(c)
  if (!clarification) return null
  const number = clarification.partnerPartNumber ?? c.partnerPartNumber ?? c.customerPartNumber
  return (
    <section
      role="alert"
      style={statusStyle('warning')}
      className="flex flex-col gap-3 rounded-xl border border-(--sb-border) bg-(--sb-bg) px-4 py-3 text-(--sb-fg) sm:flex-row sm:items-center"
    >
      <TriangleAlert aria-hidden="true" className="size-5 shrink-0" />
      <div className="min-w-0 flex-1 space-y-0.5">
        <h2 className="text-sm font-semibold">
          {number ? t('clarification.title', { number }) : t('clarification.titleUnknown')}
        </h2>
        <p className="text-sm opacity-90">{canAssign ? t('clarification.description') : t('clarification.readOnly')}</p>
      </div>
      {canAssign ? (
        <>
          <Button size="sm" variant="outline" className="shrink-0 bg-background" onClick={() => setOpen(true)}>
            <Link2 aria-hidden="true" />
            {t('clarification.action')}
          </Button>
          <AssignPartDialog approvalCase={c} partnerPartNumber={number} open={open} onOpenChange={setOpen} />
        </>
      ) : null}
    </section>
  )
}
