import {
  createCatalogElement,
  createSubmissionLevel,
  createSubmissionReason,
  deleteCatalogElement,
  deleteSubmissionLevel,
  deleteSubmissionReason,
  getCatalogElement,
  getGetCatalogElementQueryKey,
  getGetSubmissionLevelQueryKey,
  getGetSubmissionReasonQueryKey,
  getListCatalogElementsQueryKey,
  getListSubmissionLevelsQueryKey,
  getListSubmissionReasonsQueryKey,
  getSubmissionLevel,
  getSubmissionReason,
  listCatalogElements,
  listSubmissionLevels,
  listSubmissionReasons,
  updateCatalogElement,
  updateSubmissionLevel,
  updateSubmissionReason,
} from '@/api/generated/endpoints/catalog/catalog'
import {
  createPartner,
  deletePartner,
  getGetPartnerQueryKey,
  getListPartnersQueryKey,
  getPartner,
  listPartners,
  updatePartner,
} from '@/api/generated/endpoints/partners/partners'
import {
  createPart,
  deletePart,
  getGetPartQueryKey,
  getListPartsQueryKey,
  getPart,
  listParts,
  updatePart,
} from '@/api/generated/endpoints/parts/parts'
import {
  createPartMapping,
  createRelation,
  deletePartMapping,
  deleteRelation,
  getGetPartMappingQueryKey,
  getGetRelationQueryKey,
  getListPartMappingsQueryKey,
  getListRelationsQueryKey,
  getPartMapping,
  getRelation,
  listPartMappings,
  listRelations,
  updatePartMapping,
  updateRelation,
} from '@/api/generated/endpoints/relations/relations'
import {
  createSite,
  deleteSite,
  getGetSiteQueryKey,
  getListSitesQueryKey,
  getSite,
  listSites,
  updateSite,
} from '@/api/generated/endpoints/sites/sites'
import type {
  ListCatalogElementsParams,
  ListPartMappingsParams,
  ListPartnersParams,
  ListPartsParams,
  ListRelationsParams,
  ListSitesParams,
  ListSubmissionLevelsParams,
  ListSubmissionReasonsParams,
  PartMappingRequest,
} from '@/api/generated/model'

import { createResource } from './resource'

export const partsApi = createResource({
  keyPrefix: '/api/v1/parts',
  list: (params: ListPartsParams, options) => listParts(params, options),
  listKey: getListPartsQueryKey,
  get: getPart,
  getKey: getGetPartQueryKey,
  create: createPart,
  update: updatePart,
  remove: deletePart,
})

export const partnersApi = createResource({
  keyPrefix: '/api/v1/partners',
  list: (params: ListPartnersParams, options) => listPartners(params, options),
  listKey: getListPartnersQueryKey,
  get: getPartner,
  getKey: getGetPartnerQueryKey,
  create: createPartner,
  update: updatePartner,
  remove: deletePartner,
})

export const sitesApi = createResource({
  keyPrefix: '/api/v1/sites',
  list: (params: ListSitesParams, options) => listSites(params, options),
  listKey: getListSitesQueryKey,
  get: getSite,
  getKey: getGetSiteQueryKey,
  create: createSite,
  update: updateSite,
  remove: deleteSite,
})

export const relationsApi = createResource({
  keyPrefix: '/api/v1/relations',
  list: (params: ListRelationsParams, options) => listRelations(params, options),
  listKey: getListRelationsQueryKey,
  get: getRelation,
  getKey: getGetRelationQueryKey,
  create: createRelation,
  update: updateRelation,
  remove: deleteRelation,
})

/** Part mappings are nested below a relation (keys share the `/api/v1/relations` prefix). */
export function partMappingsApi(relationId: string) {
  return createResource({
    keyPrefix: '/api/v1/relations',
    list: (params: ListPartMappingsParams, options) => listPartMappings(relationId, params, options),
    listKey: (params) => getListPartMappingsQueryKey(relationId, params),
    get: (id, options) => getPartMapping(relationId, id, options),
    getKey: (id) => getGetPartMappingQueryKey(relationId, id),
    create: (input: PartMappingRequest, options) => createPartMapping(relationId, input, options),
    update: (id, input: PartMappingRequest, headers, options) =>
      updatePartMapping(relationId, id, input, headers, options),
    remove: (id, headers, options) => deletePartMapping(relationId, id, headers, options),
  })
}

export const elementsApi = createResource({
  keyPrefix: '/api/v1/catalog/elements',
  list: (params: ListCatalogElementsParams, options) => listCatalogElements(params, options),
  listKey: getListCatalogElementsQueryKey,
  get: getCatalogElement,
  getKey: getGetCatalogElementQueryKey,
  create: createCatalogElement,
  update: updateCatalogElement,
  remove: deleteCatalogElement,
})

export const levelsApi = createResource({
  keyPrefix: '/api/v1/catalog/levels',
  list: (params: ListSubmissionLevelsParams, options) => listSubmissionLevels(params, options),
  listKey: getListSubmissionLevelsQueryKey,
  get: getSubmissionLevel,
  getKey: getGetSubmissionLevelQueryKey,
  create: createSubmissionLevel,
  update: updateSubmissionLevel,
  remove: deleteSubmissionLevel,
})

export const reasonsApi = createResource({
  keyPrefix: '/api/v1/catalog/reasons',
  list: (params: ListSubmissionReasonsParams, options) => listSubmissionReasons(params, options),
  listKey: getListSubmissionReasonsQueryKey,
  get: getSubmissionReason,
  getKey: getGetSubmissionReasonQueryKey,
  create: createSubmissionReason,
  update: updateSubmissionReason,
  remove: deleteSubmissionReason,
})

export * from './types'
export { normalizePreview, policyProfilesApi, POLICY_PROFILES_URL, usePolicyPreview } from './policy-profiles'
export { ifMatch, invalidateResource } from './resource'
