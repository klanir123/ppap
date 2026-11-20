import { toast } from 'sonner'

import i18n from '@/lib/i18n'

import type { ReconciliationResult } from '../api/types'

/**
 * Toast after a profile / assignment change: the backend applies it to the own EDC immediately
 * (policies with content hash, contract definitions re-pointed) and may report the outcome.
 */
export function toastReconciliation(result: ReconciliationResult | null | undefined, savedMessage: string) {
  const t = i18n.getFixedT(null, 'masterdata')
  if (!result) {
    toast.success(savedMessage, { description: t('policyProfiles.reconciliation.applied') })
    return
  }
  const policies = result.policyIds.length
    ? t('policyProfiles.reconciliation.policies', {
        count: result.policyIds.length,
        definitions: result.contractDefinitionIds.length,
      })
    : undefined
  switch (result.status) {
    case 'FAILED':
      toast.error(savedMessage, {
        description: [t('policyProfiles.reconciliation.failed'), result.error].filter(Boolean).join(' – '),
        duration: 15_000,
      })
      return
    case 'PENDING':
      toast.info(savedMessage, { description: t('policyProfiles.reconciliation.pending') })
      return
    default:
      toast.success(savedMessage, {
        description: [t('policyProfiles.reconciliation.applied'), policies].filter(Boolean).join(' '),
      })
  }
}
