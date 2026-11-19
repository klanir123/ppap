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

/** Signature verification of a submitted revision / decision. */
export const SIGNATURE_STATES = ['VALID', 'INVALID', 'UNVERIFIED', 'NOT_SIGNED'] as const
export type SignatureState = (typeof SIGNATURE_STATES)[number]

/** Supplier self-assessment per element (i. O. / n. i. O. / entfällt). */
export const ASSESSMENTS = ['OK', 'NOK', 'NA'] as const
export type Assessment = (typeof ASSESSMENTS)[number]

/** Customer review finding per element. */
export const FINDINGS = ['ACCEPTED', 'NOT_ACCEPTED', 'ACCEPTED_WITH_CONDITION'] as const
export type Finding = (typeof FINDINGS)[number]

/** Exchange message status (EDC outbox/inbox), API values. */
export const EXCHANGE_STATES = [
  'PENDING',
  'IN_PROGRESS',
  'DELIVERED',
  'FAILED',
  'RECEIVED',
  'PROCESSED',
  'REJECTED',
] as const
export type ExchangeState = (typeof EXCHANGE_STATES)[number]

/** State of one exchange step (catalog, negotiation, …); PENDING/NOT_REACHED for steps without a record. */
export const STEP_STATES = ['OK', 'FAILED', 'SKIPPED', 'PENDING', 'NOT_REACHED'] as const
export type StepStateValue = (typeof STEP_STATES)[number]
