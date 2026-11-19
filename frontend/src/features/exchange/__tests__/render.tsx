import type { ReactElement } from 'react'

import type { MeResponse } from '@/api/generated/model'
import { CurrentUserContext } from '@/features/auth/current-user'
import { renderWithProviders } from '@/test/render'

export function renderAs(ui: ReactElement, roles: string[]) {
  const me: MeResponse = {
    userId: 'u',
    roles,
    tenant: { bpnl: 'BPNL00000003AYRE', name: 'Tier-1', roleLabel: 'Tier-1' },
  }
  return renderWithProviders(<CurrentUserContext.Provider value={me}>{ui}</CurrentUserContext.Provider>)
}
