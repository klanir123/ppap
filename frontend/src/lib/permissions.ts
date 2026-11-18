/**
 * Central place for role-based UI permissions.
 * The backend enforces authorisation; the UI only hides what a user cannot use.
 */
export const ROLES = [
  'CUSTOMER_QUALITY_ENGINEER',
  'SUPPLIER_QUALITY_MANAGER',
  'MASTERDATA_MANAGER',
  'CONNECTOR_ADMIN',
  'SYSTEM_INTEGRATION',
] as const

export type Role = (typeof ROLES)[number]

export type Permission =
  'dashboard:view' | 'approvals:view' | 'masterdata:view' | 'cockpit:view' | 'dataspace:view' | 'system:view'

const ALL_HUMAN_ROLES: readonly Role[] = [
  'CUSTOMER_QUALITY_ENGINEER',
  'SUPPLIER_QUALITY_MANAGER',
  'MASTERDATA_MANAGER',
  'CONNECTOR_ADMIN',
]

/** Permission → roles that grant it. */
const PERMISSION_ROLES: Record<Permission, readonly Role[]> = {
  'dashboard:view': ALL_HUMAN_ROLES,
  'approvals:view': ['CUSTOMER_QUALITY_ENGINEER', 'SUPPLIER_QUALITY_MANAGER', 'CONNECTOR_ADMIN'],
  'masterdata:view': ALL_HUMAN_ROLES,
  'cockpit:view': ['CONNECTOR_ADMIN'],
  'dataspace:view': ALL_HUMAN_ROLES,
  'system:view': ALL_HUMAN_ROLES,
}

export function isRole(value: string): value is Role {
  return (ROLES as readonly string[]).includes(value)
}

export function hasPermission(roles: readonly string[] | undefined, permission: Permission): boolean {
  if (!roles?.length) return false
  const allowed = PERMISSION_ROLES[permission]
  return roles.some((role) => isRole(role) && allowed.includes(role))
}
