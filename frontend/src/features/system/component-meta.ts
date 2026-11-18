import { Database, HardDrive, Network, Server, Share2, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'

const COMPONENT_ICONS: Record<string, LucideIcon> = {
  database: Database,
  edc: Network,
  objectStorage: HardDrive,
  dtr: Share2,
}

export interface ComponentInfo {
  icon: LucideIcon
  name: string
  short: string
  description?: string
}

/**
 * Display data for a component reported by `/api/v1/system/status`.
 * The backend reports free-form names; unknown ones fall back to a generic icon
 * and to the raw name (i18n defaultValue).
 */
export function useComponentInfo() {
  const { t } = useTranslation('system')
  return (name: string): ComponentInfo => {
    // dynamic key: component names come from the backend at runtime
    const key = (suffix: string) => `components.${name}.${suffix}` as 'components.database.name'
    const label = t(key('name'), { defaultValue: name })
    const description = t(key('description'), { defaultValue: '' })
    return {
      icon: COMPONENT_ICONS[name] ?? Server,
      name: label,
      short: t(key('short'), { defaultValue: label }),
      description: description || undefined,
    }
  }
}
