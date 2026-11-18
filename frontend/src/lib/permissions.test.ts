import { describe, expect, it } from 'vitest'

import { hasPermission, isRole } from './permissions'

describe('permissions', () => {
  it('only connector admins see the connector cockpit', () => {
    expect(hasPermission(['CONNECTOR_ADMIN'], 'cockpit:view')).toBe(true)
    expect(hasPermission(['CUSTOMER_QUALITY_ENGINEER'], 'cockpit:view')).toBe(false)
    expect(hasPermission(['SUPPLIER_QUALITY_MANAGER', 'MASTERDATA_MANAGER'], 'cockpit:view')).toBe(false)
  })

  it('grants a permission if any role allows it', () => {
    expect(hasPermission(['MASTERDATA_MANAGER', 'CONNECTOR_ADMIN'], 'cockpit:view')).toBe(true)
  })

  it('every human role can see the dashboard and system status', () => {
    for (const role of [
      'CUSTOMER_QUALITY_ENGINEER',
      'SUPPLIER_QUALITY_MANAGER',
      'MASTERDATA_MANAGER',
      'CONNECTOR_ADMIN',
    ]) {
      expect(hasPermission([role], 'dashboard:view')).toBe(true)
      expect(hasPermission([role], 'system:view')).toBe(true)
    }
  })

  it('technical integration clients get no UI permissions', () => {
    expect(hasPermission(['SYSTEM_INTEGRATION'], 'dashboard:view')).toBe(false)
  })

  it('denies on empty, missing or unknown roles', () => {
    expect(hasPermission([], 'dashboard:view')).toBe(false)
    expect(hasPermission(undefined, 'dashboard:view')).toBe(false)
    expect(hasPermission(['ROOT'], 'dashboard:view')).toBe(false)
    expect(isRole('ROOT')).toBe(false)
  })
})
