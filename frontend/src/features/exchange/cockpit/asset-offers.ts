import type { ConnectorContractDefinition, ConnectorPolicy } from '../api/connector'

export interface AssetOffer {
  definition: ConnectorContractDefinition
  accessPolicy?: ConnectorPolicy
  contractPolicy?: ConnectorPolicy
}

/** Asset selector of a contract definition matches the asset (empty selector = all assets). */
export function selectorMatches(selector: unknown, assetId: string): boolean {
  if (selector === undefined || selector === null) return true
  if (Array.isArray(selector) && selector.length === 0) return true
  return JSON.stringify(selector).includes(JSON.stringify(assetId))
}

/** Which contract definitions offer an asset, with their access and usage (contract) policies. */
export function offersForAsset(
  assetId: string,
  definitions: readonly ConnectorContractDefinition[] | undefined,
  policies: readonly ConnectorPolicy[] | undefined,
): AssetOffer[] {
  const byId = new Map((policies ?? []).map((p) => [p.id, p]))
  return (definitions ?? [])
    .filter((d) => selectorMatches(d.assetsSelector, assetId))
    .map((definition) => ({
      definition,
      accessPolicy: byId.get(definition.accessPolicyId),
      contractPolicy: byId.get(definition.contractPolicyId),
    }))
}
