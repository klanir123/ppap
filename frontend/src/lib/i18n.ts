import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'

import deApprovals from '@/locales/de/approvals.json'
import deAuth from '@/locales/de/auth.json'
import deCommon from '@/locales/de/common.json'
import deDashboard from '@/locales/de/dashboard.json'
import deErrors from '@/locales/de/errors.json'
import deExchange from '@/locales/de/exchange.json'
import deImport from '@/locales/de/import.json'
import deForms from '@/locales/de/forms.json'
import deTable from '@/locales/de/table.json'
import deMasterdata from '@/locales/de/masterdata.json'
import deNav from '@/locales/de/nav.json'
import dePages from '@/locales/de/pages.json'
import deStatus from '@/locales/de/status.json'
import deSystem from '@/locales/de/system.json'
import enApprovals from '@/locales/en/approvals.json'
import enAuth from '@/locales/en/auth.json'
import enCommon from '@/locales/en/common.json'
import enDashboard from '@/locales/en/dashboard.json'
import enErrors from '@/locales/en/errors.json'
import enExchange from '@/locales/en/exchange.json'
import enImport from '@/locales/en/import.json'
import enForms from '@/locales/en/forms.json'
import enTable from '@/locales/en/table.json'
import enMasterdata from '@/locales/en/masterdata.json'
import enNav from '@/locales/en/nav.json'
import enPages from '@/locales/en/pages.json'
import enStatus from '@/locales/en/status.json'
import enSystem from '@/locales/en/system.json'

export const SUPPORTED_LANGUAGES = ['de', 'en'] as const
export type Language = (typeof SUPPORTED_LANGUAGES)[number]
export const LANGUAGE_STORAGE_KEY = 'ppap.language'

export const resources = {
  de: {
    common: deCommon,
    nav: deNav,
    status: deStatus,
    auth: deAuth,
    dashboard: deDashboard,
    system: deSystem,
    pages: dePages,
    errors: deErrors,
    import: deImport,
    forms: deForms,
    table: deTable,
    masterdata: deMasterdata,
    approvals: deApprovals,
    exchange: deExchange,
  },
  en: {
    common: enCommon,
    nav: enNav,
    status: enStatus,
    auth: enAuth,
    dashboard: enDashboard,
    system: enSystem,
    pages: enPages,
    errors: enErrors,
    import: enImport,
    forms: enForms,
    table: enTable,
    masterdata: enMasterdata,
    approvals: enApprovals,
    exchange: enExchange,
  },
} as const

export const defaultNS = 'common'

function syncDocumentLanguage(lng: string) {
  if (typeof document !== 'undefined') document.documentElement.lang = lng
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    defaultNS,
    ns: Object.keys(resources.de),
    fallbackLng: 'de',
    supportedLngs: SUPPORTED_LANGUAGES,
    nonExplicitSupportedLngs: false,
    load: 'languageOnly',
    interpolation: { escapeValue: false },
    // Only an explicit user choice is persisted; otherwise the instance default (config) applies.
    detection: {
      order: ['localStorage'],
      lookupLocalStorage: LANGUAGE_STORAGE_KEY,
      caches: [],
    },
    returnNull: false,
  })

syncDocumentLanguage(i18n.resolvedLanguage ?? 'de')
i18n.on('languageChanged', syncDocumentLanguage)

function storedLanguage(): string | null {
  try {
    return window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
  } catch {
    return null
  }
}

/** Applies the instance default locale from the runtime config unless the user picked one. */
export function applyDefaultLocale(locale: string | undefined) {
  if (!locale || storedLanguage()) return
  const lng = locale.slice(0, 2).toLowerCase()
  if ((SUPPORTED_LANGUAGES as readonly string[]).includes(lng) && i18n.resolvedLanguage !== lng) {
    void i18n.changeLanguage(lng)
  }
}

/** Explicit user choice: switch and persist. */
export function setLanguage(lng: Language) {
  try {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, lng)
  } catch {
    // storage unavailable (private mode) – language still switches for this session
  }
  void i18n.changeLanguage(lng)
}

export default i18n
