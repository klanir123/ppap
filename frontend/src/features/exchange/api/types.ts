/**
 * Exchange (EDC) types – aliases of the orval-generated models (api/openapi.json is the source of truth).
 * Literal lists are checked against the generated enums.
 */
import type {
  AssignPartRequest,
  ClarificationDto,
  ExchangeMessageDetailDto,
  ExchangeMessagePage as ExchangeMessagePageDto,
  ExchangeRecordDto,
  ExchangeRecordDtoDirection,
  ExchangeRecordDtoStatus,
  ExchangeStatusDto,
  ExchangeStepDto,
  ExchangeStepDtoStatus,
  ExchangeStepDtoStep,
  LastOutboundDto,
  ListExchangeMessagesParams,
} from '@/api/generated/model'

export type ExchangeDirection = ExchangeRecordDtoDirection
export const EXCHANGE_DIRECTIONS = ['OUTBOUND', 'INBOUND'] as const satisfies readonly ExchangeDirection[]

/** Known message types (the API types `messageType` as string – unknown values are shown raw). */
export const MESSAGE_TYPES = [
  'PPAP_REQUEST',
  'PPAP_REQUEST_WITHDRAWN',
  'PPAP_STATUS_UPDATE',
  'PPAP_SUBMISSION',
  'PPAP_DECISION',
] as const
export type MessageType = (typeof MESSAGE_TYPES)[number]

/** Outbound: PENDING → IN_PROGRESS → DELIVERED | FAILED; inbound: RECEIVED → PROCESSED | REJECTED. */
export type ExchangeStatus = ExchangeRecordDtoStatus
export const EXCHANGE_STATUSES = [
  'PENDING',
  'IN_PROGRESS',
  'DELIVERED',
  'FAILED',
  'RECEIVED',
  'PROCESSED',
  'REJECTED',
] as const satisfies readonly ExchangeStatus[]

export type StepName = ExchangeStepDtoStep
export type StepStatus = ExchangeStepDtoStatus

/** Technical EDC details of a step (assetId, offerId, negotiationId, agreementId, transferProcessId, httpStatus, cachedEdr, …). */
export type StepDetails = Record<string, unknown>

export type ExchangeStep = Omit<ExchangeStepDto, 'attempt' | 'details' | 'durationMs'> & {
  attempt?: number
  details?: StepDetails | null
  durationMs?: number | null
}

export type ExchangeRecord = Omit<ExchangeRecordDto, 'steps' | 'retryable'> & {
  steps: ExchangeStep[]
  /** Server verdict whether a retry makes sense (absent in older payloads → derived from status). */
  retryable?: boolean
}

/** `GET /exchange/messages/{id}` – record plus the (canonical) notification payload. */
export type ExchangeMessageDetail = ExchangeMessageDetailDto

export type ExchangeMessagePage = ExchangeMessagePageDto
export type ExchangeMessageParams = ListExchangeMessagesParams
export type ExchangeOverview = ExchangeStatusDto

/** UC-04 A1 – a received request whose customer part number has no part mapping. */
export type CaseClarification = ClarificationDto
export type LastOutbound = LastOutboundDto
export type AssignPartInput = Required<AssignPartRequest>

// --- live events (GET /api/v1/events) -----------------------------------------------------------

export interface CaseChangedEvent {
  type: 'case.changed'
  caseId: string
  status?: string
}

export interface ExchangeChangedEvent {
  type: 'exchange.changed'
  caseId?: string | null
  messageId?: string
  status?: ExchangeStatus
}

export type LiveEvent = CaseChangedEvent | ExchangeChangedEvent
