import { useTranslation } from 'react-i18next'

import type { FlowCategory } from './model/flows'

/** Labels of message categories and record kinds (DE/EN). */
export function useFlowLabels() {
  const { t } = useTranslation(['dataspace', 'exchange'])
  return {
    category: (c: FlowCategory) => t(`legend.${c}`),
    type: (type: string) =>
      t(`exchange:messageType.${type}` as 'exchange:messageType.PPAP_REQUEST', { defaultValue: type }),
  }
}
