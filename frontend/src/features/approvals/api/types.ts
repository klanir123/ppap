/**
 * Approval case types – aliases of the orval-generated models (api/openapi.json is the source of truth).
 * Consumers import only from here; literal lists below are checked against the generated enums.
 */
import type {
  ApprovalCaseDetailDto,
  ApprovalCaseDto,
  ApprovalCaseDtoAllowedActionsItem,
  ApprovalCaseDtoOwnRole,
  ApprovalCaseDtoSource,
  ApprovalCaseDtoStatus,
  CaseRequestDto,
  CaseStatsDto,
  CompletenessDto,
  CompletenessIssueDto,
  CreateApprovalCaseRequest,
  DecisionDto,
  DecisionDtoResult,
  DocumentRefDto,
  ElementEvidenceDto,
  ElementEvidenceDtoSelfAssessment,
  ElementSnapshotDto,
  FindingDto,
  FindingDtoResult,
  FormImportPreviewDto,
  FormTemplateDto,
  IssueDto,
  ManualApprovalCaseRequest,
  RequestedElementDto,
  RequestedElementDtoRequirement,
  SignatureDto,
  SignatureDtoStatus,
  SubmissionDto,
  SubmissionDtoStatus,
  SubmissionSummaryDto,
  TimelineEntryDto,
} from '@/api/generated/model'
import type { PageData } from '@/components/data-table'

export type CaseStatus = ApprovalCaseDtoStatus
export const CASE_STATUSES = [
  'DRAFT',
  'REQUESTED',
  'IN_PROGRESS',
  'SUBMITTED',
  'IN_REVIEW',
  'APPROVED',
  'CONDITIONALLY_APPROVED',
  'REJECTED',
  'CANCELLED',
] as const satisfies readonly CaseStatus[]

export type CaseRole = ApprovalCaseDtoOwnRole
export const CASE_ROLES = ['CUSTOMER', 'SUPPLIER'] as const satisfies readonly CaseRole[]

export type CaseSource = ApprovalCaseDtoSource

/** Actions of `allowedActions` the UI offers (contract "Klarstellungen" §1); others are ignored. */
export const CASE_ACTIONS = [
  'EDIT',
  'SEND',
  'CANCEL',
  'START_WORK',
  'EDIT_SUBMISSION',
  'SUBMIT',
  'START_REVIEW',
  'REVIEW',
  'DECIDE',
  'NEW_REVISION',
] as const satisfies readonly ApprovalCaseDtoAllowedActionsItem[]
export type CaseAction = (typeof CASE_ACTIONS)[number]

export type Requirement = RequestedElementDtoRequirement
export type SelfAssessment = ElementEvidenceDtoSelfAssessment
export type SignatureStatus = SignatureDtoStatus
export type FindingResult = FindingDtoResult
export type DecisionResult = DecisionDtoResult
export const DECISION_RESULTS = [
  'APPROVED',
  'CONDITIONALLY_APPROVED',
  'REJECTED',
] as const satisfies readonly DecisionResult[]

export type EvidenceType = ElementSnapshotDto['evidenceType']

/** JSON Schema (draft 2020-12) snapshot of an element's structured values. */
export type JsonSchema = Record<string, unknown>
export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue }

export type ApprovalCase = ApprovalCaseDto
export type ApprovalCaseDetail = ApprovalCaseDetailDto
export type ApprovalRequest = CaseRequestDto
export type RequestElement = RequestedElementDto
export type ElementSnapshot = ElementSnapshotDto
/** Document reference (`id` is null for documents of received payloads until they are pulled, phase 5). */
export type DocumentDto = DocumentRefDto
export type TimelineEntry = TimelineEntryDto
export type Signature = SignatureDto
export type Issue = IssueDto
export type CompletenessIssue = CompletenessIssueDto
export type Completeness = CompletenessDto
export type ElementFinding = FindingDto
export type Decision = DecisionDto
export type Submission = SubmissionDto
export type SubmissionStatus = SubmissionDtoStatus
export type SubmissionElement = ElementEvidenceDto
export type SubmissionSummary = SubmissionSummaryDto
export type CaseStats = CaseStatsDto
export type FormTemplate = Pick<FormTemplateDto, 'code' | 'nameDe' | 'nameEn'>
export type ExcelPreview = FormImportPreviewDto

export interface CaseListParams {
  q?: string
  status?: CaseStatus[]
  ownRole?: CaseRole
  partnerBpnl?: string
  overdue?: boolean
  /** UC-04 A1: only received requests whose part could not be mapped. */
  needsClarification?: boolean
  page?: number
  size?: number
  sort?: string
}

export type CasePage = PageData<ApprovalCase>

export interface RequestElementInput {
  code: string
  requirement: Requirement
  note?: string
}

export type CreateCaseInput = CreateApprovalCaseRequest & { elements?: RequestElementInput[] }
export type ManualCaseInput = ManualApprovalCaseRequest & { elements: RequestElementInput[] }

export interface DraftElementInput {
  selfAssessment: SelfAssessment | null
  values: JsonValue | undefined
  comment: string
}

export interface ReviewElementInput {
  result: FindingResult
  comment: string
}

export interface DecisionInput {
  result: DecisionResult
  conditions: { text: string }[]
  conditionDueDate?: string
  comment?: string
  confirmation: true
}

export interface ExcelSelection {
  elementCodes: string[]
  overwrite: boolean
}

/** Result of a write that changes the case `version` (from the `ETag` header). */
export interface Versioned<T> {
  data: T
  version?: number
}
