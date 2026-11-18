import { useEffect, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { useGetConfig } from '@/api/generated/endpoints/identity/identity'
import { FullPageLoader } from '@/features/errors/FullPageLoader'
import { ErrorScreen } from '@/features/errors/ErrorScreen'
import { applyDefaultLocale } from '@/lib/i18n'

import { ConfigContext } from './config-context'
import { applyParticipantBranding } from './participant-branding'

/** Loads the public runtime config (`GET /api/v1/config`) before anything else. */
export function ConfigProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const query = useGetConfig({ query: { staleTime: Infinity, gcTime: Infinity, retry: 2 } })
  const config = query.data

  useEffect(() => {
    if (!config) return
    applyDefaultLocale(config.defaultLocale)
    applyParticipantBranding(config.participant, t('productName'))
  }, [config, t])

  if (query.isPending) return <FullPageLoader />
  if (query.isError || !config) {
    return (
      <ErrorScreen
        titleKey="config.title"
        descriptionKey="config.description"
        error={query.error}
        onRetry={() => void query.refetch()}
      />
    )
  }
  return <ConfigContext value={config}>{children}</ConfigContext>
}
