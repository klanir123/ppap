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
    DataPlaneResponse call(
            EndpointDataReference edr, String method, byte[] body, String contentType, Map<String, String> headers);

    /** Latest contract negotiations (monitoring, cockpit). */
    List<Map<String, Object>> listNegotiations(int limit);

    /** Latest transfer processes (monitoring, cockpit). */
    List<Map<String, Object>> listTransfers(int limit);
}
