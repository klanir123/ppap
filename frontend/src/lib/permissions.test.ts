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

  it('master data: only master data managers write; partners also connector admins', () => {
    expect(hasPermission(['MASTERDATA_MANAGER'], 'masterdata:write')).toBe(true)
    expect(hasPermission(['CONNECTOR_ADMIN'], 'masterdata:write')).toBe(false)
    expect(hasPermission(['CUSTOMER_QUALITY_ENGINEER'], 'masterdata:write')).toBe(false)
    expect(hasPermission(['CONNECTOR_ADMIN'], 'partners:write')).toBe(true)
    expect(hasPermission(['MASTERDATA_MANAGER'], 'partners:write')).toBe(true)
    expect(hasPermission(['SUPPLIER_QUALITY_MANAGER'], 'partners:write')).toBe(false)
    // every human role may read master data
    expect(hasPermission(['SUPPLIER_QUALITY_MANAGER'], 'masterdata:view')).toBe(true)
  })

  it('approvals: customers request and decide, suppliers supply', () => {
    expect(hasPermission(['CUSTOMER_QUALITY_ENGINEER'], 'approvals:request')).toBe(true)
    expect(hasPermission(['CUSTOMER_QUALITY_ENGINEER'], 'approvals:decide')).toBe(true)
    expect(hasPermission(['CUSTOMER_QUALITY_ENGINEER'], 'approvals:supply')).toBe(false)
    expect(hasPermission(['SUPPLIER_QUALITY_MANAGER'], 'approvals:supply')).toBe(true)
    expect(hasPermission(['SUPPLIER_QUALITY_MANAGER'], 'approvals:decide')).toBe(false)
    expect(hasPermission(['CONNECTOR_ADMIN'], 'approvals:request')).toBe(false)
  })
})
