import { useTranslation } from 'react-i18next'

/** 312 → "312 ms", 1520 → "1,5 s" (locale-aware). */
export function useDuration() {
  const { t, i18n } = useTranslation('exchange')
  const fmt = new Intl.NumberFormat(i18n.resolvedLanguage, { maximumFractionDigits: 1 })
  return (ms: number | null | undefined) =>
    ms == null
      ? undefined
      : ms < 1000
        ? t('record.durationMs', { value: fmt.format(ms) })
        : t('record.durationS', { value: fmt.format(ms / 1000) })
}
