import { ShieldOff } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { EmptyState } from '@/components/EmptyState'
import type { Permission } from '@/lib/permissions'

import { usePermission } from './current-user'

/** Route-level guard. The backend remains the authority; this only avoids dead ends. */
export function RequirePermission({ permission, children }: { permission: Permission; children: ReactNode }) {
  const allowed = usePermission(permission)
  const { t } = useTranslation('errors')
  if (allowed) return <>{children}</>
  return <EmptyState icon={ShieldOff} title={t('forbidden.title')} description={t('forbidden.description')} />
}
