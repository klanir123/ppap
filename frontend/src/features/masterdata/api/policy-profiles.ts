/**
 * Policy profiles (docs/api/cascade-transparency-api.md §3) over the orval-generated client.
 * Read: all signed-in roles (relation form); write: CONNECTOR_ADMIN.
 */
import { useQuery } from '@tanstack/react-query'

import {
  createPolicyProfile,
  deletePolicyProfile,
  getGetPolicyProfileQueryKey,
  getListPolicyProfilesQueryKey,
  getListPolicyProfilesUrl,
  getPolicyProfile,
  getPreviewPolicyProfileUrl,
  listPolicyProfiles,
  previewPolicyProfile,
  updatePolicyProfile,
} from '@/api/generated/endpoints/policy-profiles/policy-profiles'
import type { ListPolicyProfilesParams } from '@/api/generated/model'

import { createResource } from './resource'
import type { PolicyPreview, PolicyProfile, PolicyProfileInput } from './types'

export const POLICY_PROFILES_URL = getListPolicyProfilesUrl().split('?')[0]!

export type PolicyProfileParams = ListPolicyProfilesParams

export const policyProfilesApi = createResource<PolicyProfile, PolicyProfileInput, PolicyProfileParams>({
  keyPrefix: POLICY_PROFILES_URL,
  list: (params, options) => listPolicyProfiles(params, options),
  listKey: getListPolicyProfilesQueryKey,
  get: getPolicyProfile,
  getKey: getGetPolicyProfileQueryKey,
  create: createPolicyProfile,
  update: updatePolicyProfile,
  remove: deletePolicyProfile,
})

export const policyPreviewUrl = (id: string) => getPreviewPolicyProfileUrl(id)

/** Normalises the preview (array of policies or `{ policies }` or a single JSON-LD document). */
export function normalizePreview(data: unknown): PolicyPreview {
  if (Array.isArray(data)) return { policies: data.map((policy: unknown) => asEntry(policy)) }
  if (data && typeof data === 'object' && Array.isArray((data as { policies?: unknown }).policies)) {
    const obj = data as { policies: unknown[]; relations?: unknown }
    return {
      policies: obj.policies.map((p) => asEntry(p)),
      relations: Array.isArray(obj.relations) ? obj.relations.map(String) : undefined,
    }
  }
  return { policies: data === undefined || data === null ? [] : [asEntry(data)] }
}

function asEntry(value: unknown): PolicyPreview['policies'][number] {
  if (value && typeof value === 'object' && 'policy' in value) {
    const v = value as { policy: unknown; id?: string; policyId?: string; kind?: string; role?: string }
    return { id: v.id ?? v.policyId, kind: v.kind ?? v.role, policy: v.policy }
  }
  const id = value && typeof value === 'object' ? ((value as Record<string, unknown>)['@id'] as string) : undefined
  return { id, policy: value }
}

export function usePolicyPreview(id: string | undefined, version?: number) {
  return useQuery({
    queryKey: [policyPreviewUrl(id ?? ''), { version }],
    queryFn: async ({ signal }) => normalizePreview(await previewPolicyProfile(id!, { signal })),
    enabled: Boolean(id),
  })
}
