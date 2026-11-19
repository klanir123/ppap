import { CircleSlash, CloudCheck, CloudAlert, Loader2, RotateCw, ShieldCheck, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { isApiError } from '@/api/problem'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { usePermission } from '@/features/auth/current-user'
import { translateErrorCode } from '@/lib/problem-errors'
import { statusStyle } from '@/lib/status-style'
import { cn } from '@/lib/utils'

import { useFetchDocument, type DocumentDto, type TransferStatus } from '../api'
import { transferStatusOf } from './documents'

const PILL =
  'inline-flex h-5 shrink-0 items-center gap-1 rounded-full border border-(--sb-border) bg-(--sb-bg) px-2 text-[11px] font-medium whitespace-nowrap text-(--sb-fg)'

const META: Record<Exclude<TransferStatus, 'LOCAL'>, { icon: LucideIcon; token: string }> = {
  PENDING: { icon: Loader2, token: 'in-progress' },
  FETCHED: { icon: CloudCheck, token: 'delivered' },
  FAILED: { icon: CloudAlert, token: 'failed' },
  NOT_AVAILABLE: { icon: CircleSlash, token: 'unknown' },
}

/**
 * Transfer state of a received document (pull over EDC, docs/api/pull-api.md). Own uploads (LOCAL) show nothing.
 * The pill carries a tooltip explaining the state for non-experts; FETCHED adds "Hash geprüft" when the server verified it.
 */
export function TransferStatusBadge({ doc }: { doc: Pick<DocumentDto, 'id' | 'transferStatus' | 'hashVerified'> }) {
  const { t } = useTranslation('approvals')
  const status = transferStatusOf(doc)
  if (status === 'LOCAL') return null
  const { icon: Icon, token } = META[status]
  const label = t(`documents.transfer.${status}`)
  return (
    <span className="inline-flex flex-wrap items-center gap-1" data-transfer={status}>
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            style={statusStyle(token)}
            className={cn(PILL, 'cursor-default focus-visible:outline-2 focus-visible:outline-ring')}
            aria-label={`${label}. ${t(`documents.transferHint.${status}`)}`}
          >
            <Icon
              aria-hidden="true"
              className={cn('size-3', status === 'PENDING' && 'animate-spin motion-reduce:animate-none')}
              strokeWidth={2.25}
            />
            {label}
          </button>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs text-left">{t(`documents.transferHint.${status}`)}</TooltipContent>
      </Tooltip>
      {status === 'FETCHED' && doc.hashVerified ? (
        <span style={statusStyle('valid')} className={PILL} data-hash-verified="true">
          <ShieldCheck aria-hidden="true" className="size-3" strokeWidth={2.25} />
          {t('documents.hashOk')}
        </span>
      ) : null}
    </span>
  )
}

/** "Erneut abrufen" for a FAILED pull (permission-gated; the backend checks the case role). */
export function RefetchDocumentButton({ caseId, doc }: { caseId: string; doc: DocumentDto }) {
  const { t } = useTranslation('approvals')
  const allowed = usePermission('documents:fetch')
  const fetchDocument = useFetchDocument()
  if (!allowed || transferStatusOf(doc) !== 'FAILED') return null
  return (
    <Button
      variant="outline"
      size="xs"
      disabled={fetchDocument.isPending}
      aria-label={t('documents.refetchLabel', { name: doc.fileName })}
      onClick={() =>
        fetchDocument.mutate(
          { caseId, sha256: doc.sha256 },
          {
            onSuccess: () => toast.success(t('documents.refetchStarted', { name: doc.fileName })),
            onError: (error) =>
              toast.error(
                translateErrorCode(
                  isApiError(error) ? error.problem?.code : undefined,
                  isApiError(error) ? error.problem?.detail : undefined,
                ),
              ),
          },
        )
      }
    >
      {fetchDocument.isPending ? (
        <Loader2 aria-hidden="true" className="animate-spin" />
      ) : (
        <RotateCw aria-hidden="true" />
      )}
      {t('documents.refetch')}
    </Button>
  )
}
