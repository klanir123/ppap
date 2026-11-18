/** Status vocabularies with a shared visual language (see README › Design tokens). */
export const APPROVAL_STATUSES = [
  'draft',
  'requested',
  'in-progress',
  'submitted',
  'in-review',
  'approved',
  'conditionally-approved',
  'rejected',
  'cancelled',
] as const
export type ApprovalStatus = (typeof APPROVAL_STATUSES)[number]

export const HEALTH_STATUSES = ['UP', 'DOWN', 'UNKNOWN'] as const
export type HealthStatus = (typeof HEALTH_STATUSES)[number]

export function isHealthStatus(value: string): value is HealthStatus {
  return (HEALTH_STATUSES as readonly string[]).includes(value)
}

/** Worst-of aggregation: any DOWN → DOWN, any UNKNOWN → UNKNOWN, else UP. */
export function aggregateHealth(statuses: readonly string[]): HealthStatus {
  if (statuses.length === 0) return 'UNKNOWN'
  if (statuses.includes('DOWN')) return 'DOWN'
  if (statuses.some((s) => s !== 'UP')) return 'UNKNOWN'
  return 'UP'
}

export const VALIDATION_STATUSES = ['OK', 'WARNING', 'ERROR'] as const
export type ValidationStatus = (typeof VALIDATION_STATUSES)[number]

/** Catalogue matrix cell value; a missing element code means NOT_REQUIRED. */
export const REQUIREMENT_LEVELS = ['REQUIRED', 'ON_REQUEST', 'NOT_REQUIRED'] as const
export type RequirementLevel = (typeof REQUIREMENT_LEVELS)[number]
