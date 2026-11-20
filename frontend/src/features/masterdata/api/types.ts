/**
 * Master data types – aliases of the orval-generated models (api/openapi.json is the source of truth).
 */
import type {
  CatalogElementDto,
  CatalogElementRequest,
  PartDto,
  PartMappingDto,
  PartMappingRequest,
  PartRequest,
  PartnerDto,
  PartnerRequest,
  RelationDto,
  RelationRequest,
  SiteDto,
  SiteRequest,
  SubmissionLevelDto,
  SubmissionLevelRequest,
  SubmissionReasonDto,
  SubmissionReasonRequest,
} from '@/api/generated/model'
import type {
  PolicyAccessDtoType,
  PolicyConstraintDto,
  PolicyProfileDto,
  PolicyProfileRequest,
  ReconciliationDto,
} from '@/api/generated/model'
import {
  CatalogElementDtoEvidenceType,
  PartnerDtoRolesItem,
  type RelationDtoExchangeMode,
  SubmissionLevelDtoScheme,
} from '@/api/generated/model'

export type Part = PartDto
export type PartInput = PartRequest
export type Partner = PartnerDto
export type PartnerInput = PartnerRequest
export type Site = SiteDto
export type SiteInput = SiteRequest
export type Relation = RelationDto
export type RelationInput = RelationRequest
export type PartMapping = PartMappingDto
export type PartMappingInput = PartMappingRequest
export type CatalogElement = CatalogElementDto
export type CatalogElementInput = CatalogElementRequest
export type CatalogLevel = SubmissionLevelDto
export type CatalogLevelInput = SubmissionLevelRequest
export type CatalogReason = SubmissionReasonDto
export type CatalogReasonInput = SubmissionReasonRequest

export type PartnerRole = PartnerDtoRolesItem
export const PARTNER_ROLES = Object.values(PartnerDtoRolesItem)

export type ExchangeMode = RelationDtoExchangeMode
/** Default first (contract: PUSH_NOTIFY_PULL_PAYLOAD). */
export const EXCHANGE_MODES: readonly ExchangeMode[] = ['PUSH_NOTIFY_PULL_PAYLOAD', 'PUSH', 'PULL']

export type EvidenceType = CatalogElementDtoEvidenceType
export const EVIDENCE_TYPES = Object.values(CatalogElementDtoEvidenceType)

export type LevelScheme = SubmissionLevelDtoScheme
export const LEVEL_SCHEMES = Object.values(SubmissionLevelDtoScheme)

// --- policy profiles (UC-10 / E5-04, docs/api/cascade-transparency-api.md §3) -------------------

export type PolicyAccessType = PolicyAccessDtoType
export const POLICY_ACCESS_TYPES = ['BPN_GROUP', 'BPN'] as const satisfies readonly PolicyAccessType[]

/** Additional ODRL constraint `{ leftOperand, operator, rightOperand }` (shown read-only). */
export type PolicyConstraint = PolicyConstraintDto

/** `reconciliation` = effect on the own EDC (write responses only). */
export type PolicyProfile = PolicyProfileDto
export type PolicyProfileInput = PolicyProfileRequest

/** Outcome of applying a profile / assignment to the own EDC (policies + contract definitions). */
export type ReconciliationResult = ReconciliationDto

/** Preview normalised for display: `kind` = USAGE | ACCESS_PARTNER | ACCESS_SHARED, `policy` = JSON-LD. */
export interface PolicyPreview {
  policies: { id?: string; kind?: string; policy: unknown }[]
  relations?: string[]
}

/** Relation incl. the reconciliation result of a write that changed profile or exchange mode. */
export type RelationWithReconciliation = Relation
