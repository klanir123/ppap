import { useTranslation } from 'react-i18next'

import type { ExchangeMode, PartnerRole } from '../api/types'

export function PartnerRoleChip({ role }: { role: PartnerRole }) {
  const { t } = useTranslation('masterdata')
  return (
    <span className="rounded-md border bg-muted/60 px-1.5 py-0.5 text-[11px] font-medium whitespace-nowrap">
      {t(`relations.partnerRoleValues.${role}`)}
    </span>
  )
}

export function ExchangeModeText({ mode }: { mode: ExchangeMode }) {
  const { t } = useTranslation('masterdata')
  return <span className="whitespace-nowrap">{t(`relations.exchangeModeValues.${mode}`)}</span>
}
