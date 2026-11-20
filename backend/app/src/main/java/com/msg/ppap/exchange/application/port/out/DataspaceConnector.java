package com.msg.ppap.exchange.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port to the participant's dataspace connector (ADR-0001). All data exchange with partners goes through this port;
 * no direct HTTP calls between participant backends. Operations are vendor neutral; the Tractus-X EDC 0.13 adapter
 * implements them with hand-written JSON-LD requests against the management API v3.
 *
 * <p>Provisioning operations are idempotent and declarative (deterministic ids, create or update).
 */
public interface DataspaceConnector {

    /** Checks whether the connector's control plane management API is reachable and accepts our credentials. */
    ConnectorHealth health();

    /** Creates or updates an asset. */
    void ensureAsset(AssetSpec asset);

    /** Creates or updates a policy definition. */
    void ensurePolicy(PolicySpec policy);

    /** Creates or updates a contract definition. */
    void ensureContractDefinition(ContractDefinitionSpec definition);

    /**
     * Makes the group contain exactly the given BPNLs; memberships of these BPNLs in other groups are kept.
     *
     * @return BPNLs that are members of the group afterwards
     */
    Set<String> ensureBusinessPartnerGroup(String group, Set<String> bpnls);

    /**
     * Requests the partner's catalog filtered by asset id.
     *
     * @throws DataspaceException if the request fails or the asset is not offered to us
     */
    CatalogOffer requestCatalog(PartnerEndpoint partner, String assetId);

    /**
     * Offers of the partner's catalog whose asset has the given taxonomy type ({@code dct:type}), e.g. the partner's
     * Digital Twin Registry ({@code cx-taxo:DigitalTwinRegistry}).
     *
     * @return offers (may be empty if the partner offers no such asset to us)
     * @throws DataspaceException if the catalog request fails
     */
    List<CatalogOffer> requestCatalogByType(PartnerEndpoint partner, String dctType);

    /**
     * Endpoint data reference for an offer found in the partner's catalog: reuses a stored EDR of the offer's asset
     * or negotiates the given offer (no second catalog request).
     */
    EndpointDataReference obtainEndpoint(PartnerEndpoint partner, CatalogOffer offer, StepListener listener);

    /**
     * Endpoint data reference for an asset of the partner: reuses a stored EDR (token refreshed automatically) or
     * negotiates a contract and starts a transfer ({@code POST /v3/edrs}). Each step is reported to the listener.
     *
     * @throws DataspaceException if a step fails
     */
    EndpointDataReference obtainEndpoint(PartnerEndpoint partner, String assetId, StepListener listener);

    /** Forgets stored EDRs of the partner's asset (e.g. after the data plane rejected the token). */
    void invalidateEndpoint(PartnerEndpoint partner, String assetId);

    /**
     * Calls the partner's data plane with an EDR.
     *
     * @param method {@code GET} or {@code POST}
     * @param body request body ({@code null} for GET)
     * @param headers additional headers (e.g. correlation id)
     * @throws DataspaceException on network errors (HTTP error statuses are returned)
     */
    default DataPlaneResponse call(
            EndpointDataReference edr, String method, byte[] body, String contentType, Map<String, String> headers) {
        return call(edr, method, "", body, contentType, headers);
    }

    /**
     * Calls the partner's data plane with an EDR and a path/query appended to the EDR endpoint (assets with {@code
     * proxyPath} / {@code proxyQueryParams}).
     *
     * @param pathAndQuery e.g. {@code /lookup/shells?assetIds=…}; empty for the endpoint itself
     * @throws DataspaceException on network errors (HTTP error statuses are returned)
     */
    DataPlaneResponse call(
            EndpointDataReference edr,
            String method,
            String pathAndQuery,
            byte[] body,
            String contentType,
            Map<String, String> headers);

    /**
     * Streams a {@code GET} of the partner's data plane (large documents); the caller must close the result.
     *
     * @throws DataspaceException on network errors
     */
    DataPlaneStream stream(EndpointDataReference edr, String pathAndQuery, Map<String, String> headers);

    /** Ids of all contract definitions of the own connector (reconciliation of per-partner offers). */
    List<String> listContractDefinitionIds();

    /** Deletes a contract definition and its asset (no effect if absent). */
    void removeOffer(String contractDefinitionId, String assetId);

    /** Latest contract negotiations (monitoring, cockpit). */
    List<Map<String, Object>> listNegotiations(int limit);

    /** Latest transfer processes (monitoring, cockpit). */
    List<Map<String, Object>> listTransfers(int limit);

    // ------------------------------------------------------------------------- phase 6: cockpit and policies

    /** Assets of the own connector (compacted JSON-LD without context), at most {@code limit}. */
    List<Map<String, Object>> listAssets(int limit);

    /** Policy definitions of the own connector. */
    List<Map<String, Object>> listPolicies(int limit);

    /** Contract definitions of the own connector. */
    List<Map<String, Object>> listContractDefinitions(int limit);

    /** Contract negotiations, newest first, optionally filtered by state. */
    List<Map<String, Object>> listNegotiations(String state, int offset, int limit);

    /** Transfer processes, newest first, optionally filtered by state. */
    List<Map<String, Object>> listTransfers(String state, int offset, int limit);

    /**
     * The partner's complete catalog, requested live via DSP.
     *
     * @throws DataspaceException if the request fails (message of the EDC)
     */
    List<CatalogDataset> requestFullCatalog(PartnerEndpoint partner);

    /**
     * Deletes a policy definition that is no longer referenced (best effort).
     *
     * @return {@code true} if deleted
     */
    boolean removePolicy(String policyId);

    /** The policy definition exactly as sent to the management API (JSON-LD), for previews. */
    Map<String, Object> renderPolicy(PolicySpec policy);
}
