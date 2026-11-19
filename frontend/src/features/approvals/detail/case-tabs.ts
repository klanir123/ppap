import { ListAuditEventsObjectType } from '@/api/generated/model'

export const CASE_TABS = ['elements', 'revisions', 'decision', 'exchange', 'audit'] as const
export type CaseTab = (typeof CASE_TABS)[number]

export const CASE_AUDIT_OBJECT_TYPE = ListAuditEventsObjectType.APPROVAL_CASE
