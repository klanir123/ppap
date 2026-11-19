/**
 * Exchange API (docs/api/exchange-api.md) over the orval-generated client.
 */
import type { ApiRequestInit } from '@/api/fetcher'
import {
  assignPart as genAssignPart,
  getApprovalCaseExchange,
  getGetApprovalCaseExchangeUrl,
} from '@/api/generated/endpoints/approval-cases/approval-cases'
import { getStreamEventsUrl } from '@/api/generated/endpoints/events/events'
import {
  getExchangeMessage,
  getExchangeStatus,
  getGetExchangeStatusUrl,
  getListExchangeMessagesUrl,
  listExchangeMessages,
  retryExchangeMessage,
} from '@/api/generated/endpoints/exchange/exchange'
import type { ApprovalCase } from '@/features/approvals/api/types'

import type { AssignPartInput, CaseClarification, ExchangeMessageParams } from './types'

export const API_BASE = '/api/v1'
export const CASES_URL = `${API_BASE}/approval-cases`
/** Prefix of all cockpit queries (`/exchange/messages…`, `/exchange/status`). */
export const EXCHANGE_URL = getGetExchangeStatusUrl().replace(/\/status$/, '')
export const MESSAGES_URL = getListExchangeMessagesUrl().split('?')[0]!
export const EVENTS_URL = getStreamEventsUrl()

type Options = ApiRequestInit | undefined

export const caseExchangeUrl = (caseId: string) => getGetApprovalCaseExchangeUrl(caseId)

/** Exchange records of one case (any role with access to the case). */
export const listCaseExchange = (caseId: string, options?: Options) => getApprovalCaseExchange(caseId, options)

/** Cockpit: messages without payload (CONNECTOR_ADMIN). */
export const listMessages = (params: ExchangeMessageParams, options?: Options) => listExchangeMessages(params, options)

export const getMessage = (id: string, options?: Options) => getExchangeMessage(id, options)

/** Outbound FAILED → resend; inbound REJECTED → reprocess. */
export const retryMessage = (id: string) => retryExchangeMessage(id)

export const getExchangeOverview = (options?: Options) => getExchangeStatus(options)

/** UC-04 A1: assign an own part to a received request (optionally saving the part mapping). */
export const assignPart = (caseId: string, input: AssignPartInput, version?: number) =>
  genAssignPart(caseId, input, version === undefined ? undefined : { 'If-Match': `"${version}"` })

export function caseClarification(c: ApprovalCase): CaseClarification | null {
  return c.clarification ?? null
}
