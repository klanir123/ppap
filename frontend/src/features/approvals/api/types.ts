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
  DocumentRefDtoTransferStatus,
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
  SubmissionSummaryDtoExchangeMode,
  TimelineEntryDto,
  PartnerStatsDto,
  PartnerStatsDtoExchangeHealth,
  ActivityDto,
  CasePartnerDto,
  ChildCaseDto,
  ParentCaseDto,
  SubSupplierCandidateDto,
  SubSupplierReferenceDto,
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
/** Case detail incl. cascade relations (`parentCase`, `childCases`) and `actorDisplay` in the timeline. */
export type ApprovalCaseDetail = ApprovalCaseDetailDto
export type ApprovalRequest = CaseRequestDto
export type RequestElement = RequestedElementDto
export type ElementSnapshot = ElementSnapshotDto
/**
 * Transfer state of a document reference (docs/api/pull-api.md): own uploads are LOCAL; documents of received
 * payloads / request attachments are pulled over EDC (PENDING → FETCHED | FAILED; NOT_AVAILABLE = partner offers none).
 */
export type TransferStatus = DocumentRefDtoTransferStatus
export const TRANSFER_STATUSES = [
  'LOCAL',
  'PENDING',
  'FETCHED',
  'FAILED',
  'NOT_AVAILABLE',
] as const satisfies readonly TransferStatus[]

/**
 * Document reference (`id` is set once the content is stored locally). Transfer fields are optional so that
 * fresh uploads (`POST /documents`, no transfer state) can be listed with the same components.
 */
export type DocumentDto = Omit<DocumentRefDto, 'transferStatus' | 'hashVerified'> &
  Partial<Pick<DocumentRefDto, 'transferStatus' | 'hashVerified'>>

/** Exchange mode (pull-api.md): effective per revision at the time of submission. */
export type ExchangeMode = SubmissionSummaryDtoExchangeMode
export const EXCHANGE_MODES = ['PUSH_NOTIFY_PULL_PAYLOAD', 'PUSH', 'PULL'] as const satisfies readonly ExchangeMode[]
/** `actorDisplay` = user name or partner name instead of `partner:BPNL…` (UC-11). */
export type TimelineEntry = TimelineEntryDto
export type Signature = SignatureDto
export type Issue = IssueDto
export type CompletenessIssue = CompletenessIssueDto
/** `hints` = case-level notes that do not block the submission (e.g. SUB_SUPPLIER_PENDING). */
export type Completeness = CompletenessDto
export type ElementFinding = FindingDto
export type Decision = DecisionDto
/** `subSupplierReferences` = referenced decided sub-supplier cases (UC-09) – metadata only, no documents. */
export type Submission = SubmissionDto
export type SubmissionStatus = SubmissionDtoStatus
export type SubmissionElement = ElementEvidenceDto
export type SubmissionSummary = SubmissionSummaryDto
export type ExchangeHealth = PartnerStatsDtoExchangeHealth
export const EXCHANGE_HEALTH = ['OK', 'DEGRADED', 'FAILING'] as const satisfies readonly ExchangeHealth[]
/** `exchangeHealth` (last 24 h) and `lastExchangeAt` per partner (UC-11). */
export type PartnerStats = PartnerStatsDto
/** incl. `blockingChildren` = open parent cases with undecided child cases (UC-09). */
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

// --- cascade (UC-09, docs/api/cascade-transparency-api.md §1) ----------------------------------

export type CasePartnerRef = CasePartnerDto
/** Link to the parent case (own role SUPPLIER towards the customer). */
export type ParentCaseRef = ParentCaseDto
/** Child case = own request to a sub-supplier for a sub-part. */
export type ChildCaseRef = ChildCaseDto
/** Part of `PpapSubmission.subSupplierReferences` – no documents, no review results. */
export type SubSupplierReference = SubSupplierReferenceDto
/** `GET /approval-cases/{id}/sub-supplier-candidates` – decided child cases (REJECTED: information only). */
export type SubSupplierCandidate = SubSupplierCandidateDto
/** `POST /approval-cases/{parentId}/sub-requests` (same body as a normal request). */
export type SubRequestInput = CreateCaseInput

// --- activity feed (UC-11) ---------------------------------------------------------------------

/** `GET /activity?limit=` – timeline entries of all cases, newest first. */
export type ActivityEntry = ActivityDto
