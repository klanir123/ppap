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
