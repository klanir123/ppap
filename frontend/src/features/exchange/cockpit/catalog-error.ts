import { isApiError } from '@/api/problem'
import i18n from '@/lib/i18n'
import { translateErrorCode } from '@/lib/problem-errors'

/** Readable error of a failed live catalog request (code → text, EDC message, correlation id). */
export function catalogError(error: unknown): { title: string; message?: string; correlationId?: string } {
  if (!isApiError(error))
    return { title: translateErrorCode('UNEXPECTED', error instanceof Error ? error.message : undefined) }
  const code = error.problem?.code ?? `HTTP_${error.status}`
  return {
    // the generic text of this code talks about cases – the catalog needs its own wording
    title:
      code === 'PARTNER_NOT_EXCHANGE_READY' || code === 'NOT_EXCHANGE_READY'
        ? i18n.t('exchange:cockpit.catalog.partnerNotReady')
        : translateErrorCode(code, error.problem?.title),
    message: error.problem?.detail ?? undefined,
    correlationId: error.correlationId,
  }
}
