import type { AasReference, AasShellDescriptor, AasSubmodelDescriptor, TwinDto } from '../api/types'

/** specificAssetIds of the PPAP twin in display order (pull-api.md); others follow alphabetically. */
export const TWIN_ASSET_ID_ORDER = ['customerPartId', 'manufacturerPartId', 'manufacturerId', 'ppapExchangeId'] as const

export interface TwinAssetId {
  name: string
  value: string
  /** Visibility (customer BPNL); undefined = public. */
  visibleTo?: string
}

export interface TwinSubmodel {
  id: string
  idShort?: string
  revision?: number
  semanticId: string
  href?: string
  subprotocol?: string
  subprotocolBody?: string
  registeredAt?: string
}

export interface Twin {
  exchangeId: string
  shellId: string
  idShort?: string
  assetIds: TwinAssetId[]
  /** Newest revision first. */
  submodels: TwinSubmodel[]
  registeredAt?: string
  updatedAt?: string
}

/** AAS reference → first key value; plain strings pass through. */
export function referenceValue(ref: AasReference | null | undefined): string | undefined {
  if (!ref) return undefined
  if (typeof ref === 'string') return ref
  return ref.keys?.[0]?.value
}

/** Revision of a descriptor: idShort `PpapSubmission-<n>`, else the last path segment of the endpoint (`…/<exchangeId>/<n>`). */
function revisionOf(d: AasSubmodelDescriptor, href: string | undefined): number | undefined {
  const fromIdShort = /-(\d+)$/.exec(d.idShort ?? '')?.[1]
  if (fromIdShort) return Number(fromIdShort)
  const last = href?.replace(/\/+$/, '').split('/').pop()
  return last && /^\d+$/.test(last) ? Number(last) : undefined
}

/** Normalises the twin DTO (AAS 3.0 ShellDescriptor as read back from the DTR) for display. */
export function normalizeTwin(dto: TwinDto): Twin {
  const shell = (dto.shell ?? {}) as AasShellDescriptor
  const order = (name: string) => {
    const i = (TWIN_ASSET_ID_ORDER as readonly string[]).indexOf(name)
    return i === -1 ? TWIN_ASSET_ID_ORDER.length : i
  }
  const assetIds = [...(shell.specificAssetIds ?? [])]
    .map((a) => ({ name: a.name, value: a.value, visibleTo: referenceValue(a.externalSubjectId) }))
    .sort((a, b) => order(a.name) - order(b.name) || a.name.localeCompare(b.name))
  const submodels = (shell.submodelDescriptors ?? [])
    .map((d): TwinSubmodel => {
      const protocol = d.endpoints?.[0]?.protocolInformation
      return {
        id: d.id,
        idShort: d.idShort,
        semanticId: referenceValue(d.semanticId) ?? '',
        revision: revisionOf(d, protocol?.href),
        href: protocol?.href,
        subprotocol: protocol?.subprotocol,
        subprotocolBody: protocol?.subprotocolBody,
      }
    })
    .sort((a, b) => (b.revision ?? 0) - (a.revision ?? 0))
  return {
    exchangeId: dto.exchangeId,
    shellId: shell.id ?? shell.globalAssetId ?? `urn:uuid:${dto.exchangeId}`,
    idShort: shell.idShort,
    assetIds,
    submodels,
    registeredAt: dto.registeredAt,
    updatedAt: dto.updatedAt,
  }
}

/** Distinct visibility targets of the identifiers (usually exactly the customer BPNL). */
export function twinVisibility(twin: Pick<Twin, 'assetIds'>): { restricted: string[]; hasPublic: boolean } {
  const restricted = [...new Set(twin.assetIds.map((a) => a.visibleTo).filter((v): v is string => Boolean(v)))]
  return { restricted, hasPublic: twin.assetIds.some((a) => !a.visibleTo) }
}
