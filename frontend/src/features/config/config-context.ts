import { createContext, useContext } from 'react'

import type { ConfigResponse } from '@/api/generated/model'

export const ConfigContext = createContext<ConfigResponse | null>(null)

export function useAppConfig(): ConfigResponse {
  const config = useContext(ConfigContext)
  if (!config) throw new Error('useAppConfig must be used within <ConfigProvider>')
  return config
}
