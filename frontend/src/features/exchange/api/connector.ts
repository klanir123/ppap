/**
 * Connector cockpit (UC-10, docs/api/cascade-transparency-api.md §2) over the orval-generated client – read-only
 * view of the own EDC. Raw EDC objects (JSON-LD) are normalised into the shapes the UI shows.
 */
import {
  getConnectorOverview as genGetConnectorOverview,
  getGetConnectorOverviewUrl,
  listConnectorAssets as genListAssets,
  listConnectorContractDefinitions as genListContractDefinitions,
  listConnectorNegotiations as genListNegotiations,
  listConnectorPolicies as genListPolicies,
  listConnectorTransfers as genListTransfers,
  requestPartnerCatalog,
} from '@/api/generated/endpoints/connector/connector'
import type { CatalogDto, ConnectorOverviewDto, EdcObjectDto, EdcProcessDto } from '@/api/generated/model'
import type { ApiRequestInit } from '@/api/fetcher'
import type { PageData } from '@/components/data-table'

export const CONNECTOR_URL = getGetConnectorOverviewUrl().replace(/\/overview$/, '')

type Options = ApiRequestInit | undefined

/** Component status as reported by the backend (UP / DOWN / UNKNOWN, other values shown raw). */
export type ComponentState = 'UP' | 'DOWN' | 'UNKNOWN' | (string & {})

/** `edc.version` is null (management API has none); `counts` null while the EDC is down, -1 per failed query. */
export type ConnectorOverview = ConnectorOverviewDto

/** Raw EDC object as listed by the backend: id, `dct:type` / `@type`, managed flag and the JSON-LD content. */
export type EdcObject = EdcObjectDto
/** Raw negotiation / transfer process. */
export type EdcProcess = EdcProcessDto

/** Asset of the own catalog; `type` = `dct:type`, `managed` = created by the connector provisioning. */
export interface ConnectorAsset {
  id: string
  type?: string | null
  managed: boolean
  properties?: Record<string, unknown> | null
  /** Raw JSON-LD of the asset. */
  content?: Record<string, unknown>
}

export interface ConnectorPolicy {
  id: string
  managed: boolean
  /** ODRL policy (JSON-LD). */
  policy: unknown
}

export interface ConnectorContractDefinition {
  id: string
  managed: boolean
  accessPolicyId: string
  contractPolicyId: string
  /** Asset selector criteria (JSON-LD). */
  assetsSelector?: unknown
  content?: Record<string, unknown>
}

/** Counter party of a negotiation / transfer (DID or BPNL, name from master data). */
export interface CounterParty {
  participantId?: string | null
  bpnl?: string | null
  name?: string | null
}

export interface ConnectorProcess {
  id: string
  state: string
  /** CONSUMER = we requested, PROVIDER = partner requested from us. */
  type?: string | null
  counterParty?: CounterParty | null
  assetId?: string | null
  agreementId?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  errorDetail?: string | null
}
export type ConnectorNegotiation = ConnectorProcess
export type ConnectorTransfer = ConnectorProcess

export interface CatalogOffer {
  offerId: string
  policy: unknown
}

export interface CatalogDataset {
  assetId: string
  /** `dct:type` */
  type?: string | null
  description?: string | null
  properties?: Record<string, unknown> | null
  offers: CatalogOffer[]
}

/** Partner catalog, fetched live via DSP. */
export interface PartnerCatalog {
  partnerBpnl: string
  partnerName?: string | null
  participantId?: string | null
  dspEndpoint?: string | null
  durationMs: number
  requestedAt?: string
  datasets: CatalogDataset[]
}

export interface ProcessParams {
  state?: string
  page?: number
  size?: number
}

export const getConnectorOverview = (options?: Options) => genGetConnectorOverview(options)

/** JSON-LD value by local name (`edc:foo`, `https://w3id.org/edc/v0.0.1/ns/foo`, `foo`). */
export function jsonLdValue(obj: Record<string, unknown> | null | undefined, name: string): unknown {
  if (!obj) return undefined
  for (const [key, value] of Object.entries(obj)) {
    if (key === name || key.endsWith(`:${name}`) || key.endsWith(`/${name}`)) return value
  }
  return undefined
}

function asString(value: unknown): string {
  if (value && typeof value === 'object' && '@id' in value) return String((value as { '@id': unknown })['@id'])
  if (value && typeof value === 'object' && '@value' in value) return String((value as { '@value': unknown })['@value'])
  return value === undefined || value === null ? '' : String(value)
}

export function toAsset(o: EdcObject): ConnectorAsset {
  const properties = (jsonLdValue(o.content, 'properties') as Record<string, unknown> | undefined) ?? o.content
  const type = o.type ?? (asString(jsonLdValue(properties, 'type')) || undefined)
  return { id: o.id, type, managed: o.managed, properties, content: o.content }
}

export function toPolicy(o: EdcObject): ConnectorPolicy {
  return { id: o.id, managed: o.managed, policy: jsonLdValue(o.content, 'policy') ?? o.content }
}

export function toContractDefinition(o: EdcObject): ConnectorContractDefinition {
  return {
    id: o.id,
    managed: o.managed,
    accessPolicyId: asString(jsonLdValue(o.content, 'accessPolicyId')),
    contractPolicyId: asString(jsonLdValue(o.content, 'contractPolicyId')),
    assetsSelector: jsonLdValue(o.content, 'assetsSelector'),
    content: o.content,
  }
}

export function toProcess(p: EdcProcess): ConnectorProcess {
  return {
    id: p.id,
    state: p.state ?? '–',
    type: p.type,
    assetId: p.assetId,
    agreementId: p.agreementId,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
    errorDetail: p.errorDetail,
    counterParty: { participantId: p.counterPartyId, bpnl: p.partner?.bpnl, name: p.partner?.name },
  }
}

export function toCatalog(raw: CatalogDto, partnerBpnl: string): PartnerCatalog {
  return {
    partnerBpnl: raw.partner?.bpnl ?? partnerBpnl,
    partnerName: raw.partner?.name,
    participantId: raw.partnerDid,
    dspEndpoint: raw.dspEndpoint,
    durationMs: raw.durationMs,
    requestedAt: raw.requestedAt,
    datasets: raw.datasets.map((d) => ({
      assetId: d.assetId,
      type: d.type,
      properties: d.properties,
      description: asString(jsonLdValue(d.properties ?? undefined, 'description')) || undefined,
      offers: d.offers.map((offer, i) => ({
        offerId: asString((offer as Record<string, unknown>)['@id']) || `offer-${i + 1}`,
        policy: offer,
      })),
    })),
  }
}

export const listConnectorAssets = async (options?: Options) => (await genListAssets(options)).map(toAsset)

export const listConnectorPolicies = async (options?: Options) => (await genListPolicies(options)).map(toPolicy)

export const listContractDefinitions = async (options?: Options) =>
  (await genListContractDefinitions(options)).map(toContractDefinition)

/** Negotiations / transfers: the latest page (25) as rows – the UI filters and pages locally. */
function toRows(list: EdcProcess[]): PageData<ConnectorProcess> {
  const content = list.map(toProcess)
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 }
}

export const listNegotiations = async (params: ProcessParams, options?: Options) =>
  toRows(await genListNegotiations({ state: params.state, page: params.page }, options))

export const listTransfers = async (params: ProcessParams, options?: Options) =>
  toRows(await genListTransfers({ state: params.state, page: params.page }, options))

export const fetchPartnerCatalog = async (partnerBpnl: string) =>
  toCatalog(await requestPartnerCatalog({ partnerBpnl }), partnerBpnl)
