import { Link } from '@tanstack/react-router'

import type { ApprovalCase } from '../api/types'

export function CaseNumberLink({ approvalCase }: { approvalCase: Pick<ApprovalCase, 'id' | 'caseNumber'> }) {
  return (
    <Link
      to="/approvals/$caseId"
      params={{ caseId: approvalCase.id }}
      className="rounded-sm font-mono text-xs font-semibold whitespace-nowrap text-foreground underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-ring"
    >
      {approvalCase.caseNumber}
    </Link>
  )
}
