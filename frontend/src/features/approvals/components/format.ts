import { useTranslation } from 'react-i18next'

export function formatBytes(bytes: number, lng?: string): string {
  const fmt = new Intl.NumberFormat(lng, { maximumFractionDigits: 1 })
  if (bytes < 1024) return `${fmt.format(bytes)} B`
  if (bytes < 1024 * 1024) return `${fmt.format(bytes / 1024)} KB`
  return `${fmt.format(bytes / 1024 / 1024)} MB`
}

/** `2026-12-15` → Date at local midnight (no UTC shift). */
export function parseLocalDate(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y!, (m ?? 1) - 1, d ?? 1)
}

/** Locale-aware formatters bound to the active language. */
export function useFormat() {
  const { i18n } = useTranslation()
  const lng = i18n.resolvedLanguage
  const date = new Intl.DateTimeFormat(lng, { dateStyle: 'medium' })
  const dateTime = new Intl.DateTimeFormat(lng, { dateStyle: 'medium', timeStyle: 'short' })
  const number = new Intl.NumberFormat(lng, { maximumFractionDigits: 1 })
  const percent = new Intl.NumberFormat(lng, { style: 'percent', maximumFractionDigits: 0 })
  return {
    lng,
    date: (iso: string | null | undefined) =>
      iso ? date.format(iso.length <= 10 ? parseLocalDate(iso) : new Date(iso)) : '–',
    dateTime: (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '–'),
    number: (n: number) => number.format(n),
    percent: (ratio: number) => percent.format(ratio),
    bytes: (n: number) => formatBytes(n, lng),
  }
}

/** Localised catalogue/part name. */
export function localized(entry: { nameDe?: string; nameEn?: string } | undefined, lng: string | undefined): string {
  if (!entry) return ''
  return (lng === 'en' && entry.nameEn ? entry.nameEn : entry.nameDe) ?? ''
}

/** `sha256:abcdef…` / `abcdef…` → first 12 hex chars. */
export function shortHash(hash: string): string {
  const raw = hash.replace(/^sha256:/i, '')
  return raw.slice(0, 12)
}

/** Relative time ("vor 5 Min.") with the absolute time in `title` and `dateTime`. */
export function useRelativeTime() {
  const { i18n } = useTranslation()
  const fmt = useFormat()
  const rtf = new Intl.RelativeTimeFormat(i18n.resolvedLanguage, { numeric: 'auto', style: 'short' })
  return (iso: string, now = Date.now()) => {
    const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
    const abs = Math.abs(seconds)
    if (abs < 60) return rtf.format(seconds, 'second')
    if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute')
    if (abs < 86_400) return rtf.format(Math.round(seconds / 3600), 'hour')
    if (abs < 7 * 86_400) return rtf.format(Math.round(seconds / 86_400), 'day')
    return fmt.date(iso)
  }
}
