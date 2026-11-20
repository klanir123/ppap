import { useMutation } from '@tanstack/react-query'
import { FileDown, Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'

import { approvalsApi, type ApprovalCase } from '../api'
import { saveBlob } from './documents'
import { toastProblem } from './problem'

/** Downloads the signed audit export (`audit-<caseNumber>.json`, JWS in `X-Signature`). */
export function AuditExportButton({
  approvalCase,
  variant = 'outline',
}: {
  approvalCase: Pick<ApprovalCase, 'id' | 'caseNumber'>
  variant?: 'outline' | 'ghost'
}) {
  const { t } = useTranslation('approvals')
  const download = useMutation({
    meta: { silent: true },
    mutationFn: () => approvalsApi.getAuditExport(approvalCase.id, approvalCase.caseNumber),
    onSuccess: (result) => {
      saveBlob(result.blob, result.fileName)
      toast.success(t('audit.exported', { fileName: result.fileName }), {
        description: result.signature ? t('audit.signed') : undefined,
      })
    },
    onError: (error) => toastProblem(error, t('audit.exportFailed')),
  })
  return (
    <Button
      variant={variant}
      size="sm"
      onClick={() => download.mutate()}
      disabled={download.isPending}
      data-action="audit-export"
    >
      {download.isPending ? <Loader2 aria-hidden="true" className="animate-spin" /> : <FileDown aria-hidden="true" />}
      {t('audit.export')}
    </Button>
  )
}
