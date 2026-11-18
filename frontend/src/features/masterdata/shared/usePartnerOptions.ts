import { useTranslation } from 'react-i18next'

import type { Option } from '@/components/forms'
import { useAppConfig } from '@/features/config/config-context'

import { partnersApi } from '../api'

/** Owner/partner choices: own company first, then active partners (max. 200 – API page limit). */
export function usePartnerOptions({ includeOwn }: { includeOwn: boolean }): { options: Option[]; isLoading: boolean } {
  const { t } = useTranslation('masterdata')
  const { participant } = useAppConfig()
  const query = partnersApi.usePage({ active: true, size: 200, sort: ['name,asc'] })
  const partners = (query.data?.content ?? []).map((p) => ({ value: p.bpnl, label: `${p.name} · ${p.bpnl}` }))
  const own = { value: participant.bpnl, label: `${t('sites.ownTenant')} · ${participant.name} · ${participant.bpnl}` }
  return { options: includeOwn ? [own, ...partners] : partners, isLoading: query.isPending }
}
