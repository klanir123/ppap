import type { ListAuditEventsObjectType } from '@/api/generated/model'

export const CASE_TABS = ['elements', 'revisions', 'decision', 'exchange', 'audit'] as const
export type CaseTab = (typeof CASE_TABS)[number]

/** Contract §3: `APPROVAL_CASE` – not yet in the generated `objectType` enum of GET /audit-events (spec gap). */
export const CASE_AUDIT_OBJECT_TYPE = 'APPROVAL_CASE' as ListAuditEventsObjectType
