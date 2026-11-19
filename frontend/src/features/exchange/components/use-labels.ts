import { useTranslation } from 'react-i18next'

/** Message type label (DE/EN); unknown types are shown as they come. */
export function useMessageTypeLabel() {
  const { t } = useTranslation('exchange')
  return (type: string) => t(`messageType.${type}` as 'messageType.PPAP_REQUEST', { defaultValue: type })
}
