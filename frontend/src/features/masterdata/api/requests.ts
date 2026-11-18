import type {
  CatalogElement,
  CatalogElementInput,
  CatalogLevel,
  CatalogLevelInput,
  CatalogReason,
  CatalogReasonInput,
  Part,
  PartInput,
  Partner,
  PartnerInput,
  Relation,
  RelationInput,
  Site,
  SiteInput,
} from './types'

/** DTO → request body (used for reactivation: PUT with the unchanged fields + `active: true`). */
export const partRequest = (p: Part): PartInput => ({
  partNumber: p.partNumber,
  nameDe: p.nameDe,
  nameEn: p.nameEn,
  drawingNumber: p.drawingNumber,
  drawingRevision: p.drawingRevision,
  material: p.material,
  catenaXId: p.catenaXId,
})

export const partnerRequest = (p: Partner): PartnerInput => ({
  bpnl: p.bpnl,
  name: p.name,
  roles: p.roles,
  dspEndpoint: p.dspEndpoint,
  did: p.did,
  contactEmail: p.contactEmail,
})

export const siteRequest = (s: Site): SiteInput => ({
  bpns: s.bpns,
  name: s.name,
  ownerBpnl: s.ownerBpnl,
  street: s.street,
  postalCode: s.postalCode,
  city: s.city,
  country: s.country,
})

export const relationRequest = (r: Relation): RelationInput => ({
  partnerBpnl: r.partnerBpnl,
  partnerRole: r.partnerRole,
  exchangeMode: r.exchangeMode,
  policyProfile: r.policyProfile,
})

const entry = (e: CatalogReason) => ({
  code: e.code,
  nameDe: e.nameDe,
  nameEn: e.nameEn,
  descriptionDe: e.descriptionDe,
  descriptionEn: e.descriptionEn,
  sortOrder: e.sortOrder,
})

export const elementRequest = (e: CatalogElement): CatalogElementInput => ({
  ...entry(e),
  referenceVda: e.referenceVda,
  referenceAiag: e.referenceAiag,
  evidenceType: e.evidenceType,
  valueSchema: e.valueSchema,
})

export const levelRequest = (l: CatalogLevel): CatalogLevelInput => ({
  ...entry(l),
  scheme: l.scheme,
  requirements: l.requirements,
})

export const reasonRequest = (r: CatalogReason): CatalogReasonInput => entry(r)
