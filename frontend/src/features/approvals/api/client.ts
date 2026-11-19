/**
 * Approval API over the orval-generated client. Writes chain optimistic locking:
 * `If-Match: "<version>"` in, new version from the `ETag` header out (contract "Klarstellungen" §5).
 */
import {
  addRequestAttachment as genAddRequestAttachment,
  cancelApprovalCase,
  captureManualApprovalCase,
  createApprovalCase,
  decideApprovalCase,
  fetchApprovalCaseDocument,
  getApprovalCase,
  getApprovalCaseStats,
  getListApprovalCasesUrl,
  listApprovalCases,
  recordElementFinding,
  removeRequestAttachment as genRemoveRequestAttachment,
  replaceRequestElements,
  sendApprovalCase,
  startReview,
  startWork,
  updateApprovalCase,
} from '@/api/generated/endpoints/approval-cases/approval-cases'
import {
  getDocumentContent as genGetDocumentContent,
  uploadDocument as genUploadDocument,
} from '@/api/generated/endpoints/documents/documents'
import { listFormTemplates as genListFormTemplates } from '@/api/generated/endpoints/form-templates/form-templates'
import {
  applyExcelImport,
  attachDraftElementDocument,
  createRevision,
  detachDraftElementDocument,
  getGetDecisionPayloadUrl,
  getDraftSubmission,
  getSubmission as genGetSubmission,
  getGetSubmissionPayloadUrl,
  listSubmissions as genListSubmissions,
  previewExcelImport,
  submitDraft as genSubmitDraft,
  updateDraftElement,
} from '@/api/generated/endpoints/submissions/submissions'
import { apiFetchResponse, type ApiRequestInit } from '@/api/fetcher'
import type { ElementEvidenceRequest, ListApprovalCasesParams } from '@/api/generated/model'

import type {
  ApprovalCase,
  ApprovalCaseDetail,
  CaseListParams,
  CasePage,
  CreateCaseInput,
  DecisionInput,
  DraftElementInput,
  ExcelSelection,
  ManualCaseInput,
  RequestElementInput,
  ReviewElementInput,
  Versioned,
} from './types'

export const API_BASE = '/api/v1'
/** URL prefix of all approval queries (generated keys start with the URL). */
export const CASES_URL = getListApprovalCasesUrl().split('?')[0]!

type Options = ApiRequestInit | undefined

export function ifMatchHeader(version: number | undefined): { 'If-Match'?: string } | undefined {
  return version === undefined ? undefined : { 'If-Match': `"${version}"` }
}

/** `ETag: "5"` / `W/"5"` → 5 */
export function parseEtag(value: string | null): number | undefined {
  if (!value) return undefined
  const match = /(\d+)/.exec(value)
  return match ? Number(match[1]) : undefined
}

/** Runs a generated write and captures the new case version from the `ETag` header. */
async function versioned<T>(call: (options: ApiRequestInit) => Promise<T>): Promise<Versioned<T>> {
  let version: number | undefined
  const data = await call({ onResponse: (response) => (version = parseEtag(response.headers.get('ETag'))) })
  return { data, version }
}

// --- cases --------------------------------------------------------------------------------------

export function listCases(params: CaseListParams, options?: Options) {
  const query: ListApprovalCasesParams & { needsClarification?: boolean } = {
    q: params.q,
    status: params.status?.length ? params.status : undefined,
    ownRole: params.ownRole,
    partnerBpnl: params.partnerBpnl,
    overdue: params.overdue || undefined,
    needsClarification: params.needsClarification || undefined,
    page: params.page,
    size: params.size,
    sort: params.sort ? [params.sort] : undefined,
  }
  return listApprovalCases(query, options) as Promise<CasePage>
}

export const getCase = (id: string, options?: Options) => getApprovalCase(id, options)

export const getCaseStats = (options?: Options) => getApprovalCaseStats(options)

export const createCase = (input: CreateCaseInput) => createApprovalCase(input)

export const createManualCase = (input: ManualCaseInput) => captureManualApprovalCase(input)

/** Updates a DRAFT (case fields; elements via `updateRequestElements`). */
export const updateCase = (
  id: string,
  input: Omit<CreateCaseInput, 'elements' | 'partId' | 'relationId'>,
  version?: number,
) =>
  versioned((o) =>
    updateApprovalCase(
      id,
      { dueDate: input.dueDate, levelCode: input.levelCode, reasonCode: input.reasonCode, remarks: input.remarks },
      ifMatchHeader(version),
      o,
    ),
  )

export const updateRequestElements = (id: string, elements: RequestElementInput[], version?: number) =>
  versioned((o) => replaceRequestElements(id, elements, ifMatchHeader(version), o))

export const addRequestAttachment = (id: string, documentId: string, version?: number) =>
  versioned((o) => genAddRequestAttachment(id, { documentId }, ifMatchHeader(version), o))

export const removeRequestAttachment = (id: string, documentId: string, version?: number) =>
  versioned((o) => genRemoveRequestAttachment(id, documentId, ifMatchHeader(version), o))

/** Simple state transitions without a domain body. */
export type TransitionAction = 'send' | 'start-work' | 'start-review'

const TRANSITIONS = { send: sendApprovalCase, 'start-work': startWork, 'start-review': startReview } as const

export const transitionCase = (id: string, action: TransitionAction, version?: number): Promise<ApprovalCaseDetail> =>
  TRANSITIONS[action](id, ifMatchHeader(version))

export const cancelCase = (id: string, reason: string, version?: number) =>
  cancelApprovalCase(id, { reason }, ifMatchHeader(version))

// --- submissions --------------------------------------------------------------------------------

export const listSubmissions = (id: string, options?: Options) => genListSubmissions(id, options)

/** `revision` = number or `draft`. */
export const getSubmission = (id: string, revision: number | 'draft', options?: Options) =>
  revision === 'draft' ? getDraftSubmission(id, options) : genGetSubmission(id, revision, options)

export const newRevision = (id: string, version?: number) => createRevision(id, ifMatchHeader(version))

function evidenceRequest(input: DraftElementInput): ElementEvidenceRequest {
  return {
    // null clears the assessment
    selfAssessment: input.selfAssessment,
    values: input.values,
    comment: input.comment,
  }
}

export const putDraftElement = (id: string, code: string, input: DraftElementInput, version?: number) =>
  versioned((o) => updateDraftElement(id, code, evidenceRequest(input), ifMatchHeader(version), o))

export const addDraftDocument = (id: string, code: string, documentId: string, version?: number) =>
  versioned((o) => attachDraftElementDocument(id, code, { documentId }, ifMatchHeader(version), o))

export const removeDraftDocument = (id: string, code: string, documentId: string, version?: number) =>
  versioned((o) => detachDraftElementDocument(id, code, documentId, ifMatchHeader(version), o))

export const submitDraft = (id: string, version?: number) =>
  genSubmitDraft(id, { confirmation: true }, ifMatchHeader(version))

export interface PayloadDownload {
  blob: Blob
  hash?: string
  signature?: string
}

/** Canonical payload as stored (raw bytes – re-serialising JSON would change the hash). */
async function downloadPayload(url: string): Promise<PayloadDownload> {
  const response = await apiFetchResponse(url, { headers: { Accept: 'application/json' } })
  return {
    blob: await response.blob(),
    hash: response.headers.get('X-Payload-Hash') ?? undefined,
    signature: response.headers.get('X-Signature') ?? undefined,
  }
}

export const getSubmissionPayload = (id: string, revision: number) =>
  downloadPayload(getGetSubmissionPayloadUrl(id, revision))

export const getDecisionPayload = (id: string, revision: number) =>
  downloadPayload(getGetDecisionPayloadUrl(id, revision))

// --- excel import -------------------------------------------------------------------------------

export const listFormTemplates = (options?: Options) => genListFormTemplates(options)

export const excelPreview = (id: string, file: File, templateCode?: string) =>
  previewExcelImport(id, { file }, templateCode ? { templateCode } : undefined)

export const excelApply = (id: string, file: File, templateCode: string, selection: ExcelSelection, version?: number) =>
  versioned((o) =>
    applyExcelImport(id, { templateCode }, { file, selection: JSON.stringify(selection) }, ifMatchHeader(version), o),
  )

// --- review & decision --------------------------------------------------------------------------

export const putReviewElement = (id: string, code: string, input: ReviewElementInput, version?: number) =>
  versioned((o) => recordElementFinding(id, code, input, ifMatchHeader(version), o))

export const postDecision = (id: string, input: DecisionInput, version?: number) =>
  decideApprovalCase(id, input, ifMatchHeader(version))

// --- documents ----------------------------------------------------------------------------------

export const uploadDocument = (file: File, options?: Options) => genUploadDocument({ file }, options)

/** Document content with auth (blob → object URL for previews / downloads). */
export const getDocumentContent = (id: string, disposition: 'inline' | 'attachment' = 'inline') =>
  genGetDocumentContent(id, { disposition }, { headers: { Accept: '*/*' } })

/** `sha256:<hex>` → `<hex>` (path segment of the document endpoints). */
export const sha256Segment = (sha256: string) => sha256.replace(/^sha256:/i, '').toLowerCase()

/** Re-triggers the EDC pull of a received document (customer: evidence, supplier: request attachments). */
export const fetchCaseDocument = (caseId: string, sha256: string) =>
  fetchApprovalCaseDocument(caseId, sha256Segment(sha256))

export type { ApprovalCase }
