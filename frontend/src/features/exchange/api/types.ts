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
  ExchangeStepDtoTarget,
  LastOutboundDto,
  ListExchangeMessagesParams,
  TwinDto as GeneratedTwinDto,
} from '@/api/generated/model'

export type ExchangeDirection = ExchangeRecordDtoDirection
export const EXCHANGE_DIRECTIONS = ['OUTBOUND', 'INBOUND'] as const satisfies readonly ExchangeDirection[]

/** Notifications (EDC push of a message envelope). */
export const NOTIFICATION_TYPES = [
  'PPAP_REQUEST',
  'PPAP_REQUEST_WITHDRAWN',
  'PPAP_STATUS_UPDATE',
  'PPAP_SUBMISSION',
  'PPAP_SUBMISSION_AVAILABLE',
  'PPAP_DECISION',
] as const

/** Phase 5 record kinds that are no notifications: pulls by the customer/supplier and the local twin registration. */
export const PULL_KINDS = ['PULL_SUBMISSION', 'PULL_DOCUMENT'] as const
export const LOCAL_KINDS = ['TWIN_REGISTRATION'] as const

/** Known record kinds (the API types `messageType` as string – unknown values are shown raw). */
export const MESSAGE_TYPES = [...NOTIFICATION_TYPES, ...PULL_KINDS, ...LOCAL_KINDS] as const
export type MessageType = (typeof MESSAGE_TYPES)[number]
export type PullKind = (typeof PULL_KINDS)[number]

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

/** Phase 5 step types (pull-api.md). */
export const PULL_STEPS = [
  'DTR_LOOKUP',
  'SHELL_DESCRIPTOR',
  'SUBMODEL_FETCH',
  'HASH_CHECK',
  'SIGNATURE_CHECK',
  'DOCUMENT_FETCH',
  'TWIN_REGISTRATION',
] as const satisfies readonly ExchangeStepDtoStep[]
export type StepName = ExchangeStepDtoStep
/** What a step works on – repeated EDC steps of a pull belong to different targets (DTR, submodel, document). */
export type StepTarget = ExchangeStepDtoTarget
export type StepStatus = ExchangeStepDtoStatus

/** Technical EDC details of a step (assetId, offerId, negotiationId, agreementId, transferProcessId, httpStatus, cachedEdr, …). */
export type StepDetails = Record<string, unknown>

export type ExchangeStep = Omit<ExchangeStepDto, 'attempt' | 'details' | 'durationMs' | 'target'> & {
  /** Absent in older records → derived from the step. */
  target?: StepTarget
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

// --- digital twin (GET /api/v1/twins/{exchangeId}, supplier) -----------------------------------

/** `{ exchangeId, caseId, registeredAt, updatedAt, shell }` – `shell` is the AAS 3.0 ShellDescriptor (untyped). */
export type TwinDto = GeneratedTwinDto

/** AAS reference (`{ type, keys: [{ type, value }] }`) or a plain value. */
export type AasReference = string | { type?: string; keys?: { type?: string; value: string }[] }

/** The parts of an AAS 3.0 ShellDescriptor the UI reads (normalised in `model/twin.ts`). */
export interface AasShellDescriptor {
  id?: string
  idShort?: string
  globalAssetId?: string
  specificAssetIds?: { name: string; value: string; externalSubjectId?: AasReference | null }[]
  submodelDescriptors?: AasSubmodelDescriptor[]
}

export interface AasSubmodelDescriptor {
  id: string
  idShort?: string
  semanticId?: AasReference
  endpoints?: {
    interface?: string
    protocolInformation?: {
      href?: string
      endpointProtocol?: string
      subprotocol?: string
      subprotocolBody?: string
      subprotocolBodyEncoding?: string
    }
  }[]
}

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
