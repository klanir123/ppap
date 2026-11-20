package com.msg.ppap.exchange.adapter.out.edc;

import static com.msg.ppap.exchange.adapter.out.edc.JsonLd.EDC_NS;
import static com.msg.ppap.exchange.adapter.out.edc.JsonLd.TX_NS;

import com.msg.ppap.exchange.adapter.out.edc.ManagementApi.ManagementApiUnavailable;
import com.msg.ppap.exchange.adapter.out.edc.ManagementApi.Response;
import com.msg.ppap.exchange.application.port.out.AssetSpec;
import com.msg.ppap.exchange.application.port.out.CatalogDataset;
import com.msg.ppap.exchange.application.port.out.CatalogOffer;
import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.ContractDefinitionSpec;
import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataPlaneStream;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.application.port.out.StepListener;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link DataspaceConnector} for the Tractus-X EDC 0.13 management API v3 (ADR-0001), with hand-written JSON-LD
 * requests. Every request mirrors a call that is proven in our stack ({@code infra/scripts/edc-smoke.py}):
 *
 * <ul>
 *   <li>provisioning: {@code POST} (create) and on {@code 409} {@code PUT} (update) of assets, policy definitions and
 *       contract definitions; business partner groups via {@code /v3/business-partner-groups};
 *   <li>consumer: catalog over DSP 2025-1 with {@code counterPartyId} = partner DID, {@code POST /v3/edrs}
 *       (negotiation + transfer), EDR lookup {@code POST /v3/edrs/request} (filter asset id and provider DID) and
 *       {@code GET /v3/edrs/{id}/dataaddress?auto_refresh=true}; data plane call with {@code Authorization: <token>}.
 * </ul>
 *
 * <p>EDR caching: the EDC stores EDRs itself; a stored EDR of the same provider and asset is reused (token refreshed
 * by the EDC), so catalog and negotiation are only needed for the first message to a partner or after invalidation.
 */
@Component
public class TractusXEdcAdapter implements DataspaceConnector {

    private static final Logger LOG = LoggerFactory.getLogger(TractusXEdcAdapter.class);
    static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);
    static final String ASSET_QUERY_PATH = "/v3/assets/request";
    static final String GROUPS_PATH = "/v3/business-partner-groups";
    static final String DCT_TYPE = "http://purl.org/dc/terms/type";
    static final String CX_TAXO_NS = "https://w3id.org/catenax/taxonomy#";

    private final EdcProperties properties;
    private final ManagementApi api;
    private final HttpClient dataPlane;

    public TractusXEdcAdapter(EdcProperties properties) {
        this.properties = properties;
        this.api = new ManagementApi(properties.managementUrl(), properties.apiKey(), properties.requestTimeout());
        this.dataPlane = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(properties.requestTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    // ------------------------------------------------------------------------------------------------ health

    @Override
    public ConnectorHealth health() {
        URI uri = api.uri(ASSET_QUERY_PATH);
        long start = System.nanoTime();
        try {
            Response response = api.send("POST", ASSET_QUERY_PATH, JsonLd.querySpec(null, null, 1), HEALTH_TIMEOUT);
            if (!response.ok()) {
                return ConnectorHealth.down(uri.toString(), elapsed(start), "HTTP " + response.status());
            }
            return ConnectorHealth.up(uri.toString(), elapsed(start));
        } catch (RuntimeException ex) {
            return ConnectorHealth.down(uri.toString(), elapsed(start), ManagementApi.describe(ex));
        }
    }

    // ------------------------------------------------------------------------------------------ provisioning

    @Override
    public void ensureAsset(AssetSpec asset) {
        var properties = new LinkedHashMap<String, Object>();
        if (asset.type() != null) {
            properties.put("dct:type", Map.of("@id", asset.type()));
        }
        if (asset.version() != null) {
            properties.put("cx-common:version", asset.version());
        }
        if (asset.description() != null) {
            properties.put("description", asset.description());
        }
        var dataAddress = new LinkedHashMap<String, Object>();
        dataAddress.put("@type", "DataAddress");
        dataAddress.putAll(asset.dataAddress());
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.assetContext());
        body.put("@id", asset.id());
        body.put("properties", properties);
        body.put("dataAddress", dataAddress);
        upsert("/v3/assets", "/v3/assets", body, "asset " + asset.id());
    }

    @Override
    public Map<String, Object> renderPolicy(PolicySpec policy) {
        List<Map<String, Object>> constraints = policy.constraints().stream()
                .map(c -> Map.<String, Object>of(
                        "leftOperand", c.leftOperand(), "operator", c.operator(), "rightOperand", c.rightOperand()))
                .toList();
        var permission = new LinkedHashMap<String, Object>();
        permission.put("action", policy.action());
        if (constraints.size() == 1) {
            permission.put("constraint", constraints.getFirst());
        } else if (!constraints.isEmpty()) {
            permission.put("constraint", Map.of("and", constraints));
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.policyContext());
        body.put("@type", "PolicyDefinition");
        body.put("@id", policy.id());
        body.put("policy", Map.of("@type", "Set", "permission", List.of(permission)));
        return body;
    }

    @Override
    public void ensurePolicy(PolicySpec policy) {
        Map<String, Object> body = renderPolicy(policy);
        Response created = api.post("/v3/policydefinitions", body);
        // policy ids are content-addressed by the caller: an existing id has the same content. The EDC answers 409,
        // or 400 "referenced by a Contract Definition" (referenced policies are immutable) for existing ids.
        boolean exists = created.status() == 409
                || (created.status() == 400 && created.raw().contains("referenced"));
        if (!created.ok() && !exists) {
            throw provisioningFailure("create policy " + policy.id(), created);
        }
    }

    @Override
    public void ensureContractDefinition(ContractDefinitionSpec definition) {
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.edcContext());
        body.put("@id", definition.id());
        body.put("accessPolicyId", definition.accessPolicyId());
        body.put("contractPolicyId", definition.contractPolicyId());
        body.put("assetsSelector", List.of(JsonLd.filter(EDC_NS + "id", definition.assetId())));
        upsert("/v3/contractdefinitions", "/v3/contractdefinitions", body, "contract definition " + definition.id());
    }

    @Override
    public Set<String> ensureBusinessPartnerGroup(String group, Set<String> bpnls) {
        Set<String> current = new TreeSet<>();
        Response members = api.get(GROUPS_PATH + "/group/" + encode(group));
        if (members.ok()) {
            current.addAll(JsonLd.texts(members.body(), TX_NS + "bpns"));
        } else if (members.status() != 404) {
            throw provisioningFailure("read group " + group, members);
        }
        for (String bpn : bpnls) {
            if (!current.contains(bpn)) {
                Set<String> groups = groupsOf(bpn);
                boolean known = groups != null;
                Set<String> next = known ? new TreeSet<>(groups) : new TreeSet<>();
                next.add(group);
                Response response = known
                        ? api.put(GROUPS_PATH, groupEntry(bpn, next))
                        : api.post(GROUPS_PATH, groupEntry(bpn, next));
                if (!response.ok()) {
                    throw provisioningFailure("add " + bpn + " to group " + group, response);
                }
            }
        }
        for (String bpn : current) {
            if (!bpnls.contains(bpn)) {
                Set<String> groups = groupsOf(bpn);
                Set<String> remaining = groups == null ? new TreeSet<>() : new TreeSet<>(groups);
                remaining.remove(group);
                Response response = remaining.isEmpty()
                        ? api.delete(GROUPS_PATH + "/" + encode(bpn))
                        : api.put(GROUPS_PATH, groupEntry(bpn, remaining));
                if (!response.ok() && response.status() != 404) {
                    throw provisioningFailure("remove " + bpn + " from group " + group, response);
                }
            }
        }
        return new TreeSet<>(bpnls);
    }

    private Set<String> groupsOf(String bpn) {
        Response response = api.get(GROUPS_PATH + "/" + encode(bpn));
        if (response.status() == 404) {
            return null;
        }
        if (!response.ok()) {
            throw provisioningFailure("read groups of " + bpn, response);
        }
        return new LinkedHashSet<>(JsonLd.texts(response.body(), TX_NS + "groups"));
    }

    private static Map<String, Object> groupEntry(String bpn, Set<String> groups) {
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", Map.of("tx", TX_NS));
        body.put("@id", bpn);
        body.put("tx:groups", List.copyOf(groups));
        return body;
    }

    private void upsert(String createPath, String updatePath, Object body, String what) {
        Response created = api.post(createPath, body);
        if (created.ok()) {
            LOG.debug("EDC: created {}", what);
            return;
        }
        if (created.status() != 409) {
            throw provisioningFailure("create " + what, created);
        }
        Response updated = api.put(updatePath, body);
        if (!updated.ok()) {
            throw provisioningFailure("update " + what, updated);
        }
    }

    private static IllegalStateException provisioningFailure(String action, Response response) {
        return new IllegalStateException(
                "EDC: cannot " + action + ": HTTP " + response.status() + " " + abbreviate(response.raw()));
    }

    // ------------------------------------------------------------------------------------------------ consumer

    @Override
    public CatalogOffer requestCatalog(PartnerEndpoint partner, String assetId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.edcContext());
        body.put("@type", "CatalogRequest");
        body.put("counterPartyAddress", partner.dspEndpoint());
        body.put("counterPartyId", partner.did());
        body.put("protocol", JsonLd.DSP_PROTOCOL);
        body.put("querySpec", Map.of("filterExpression", List.of(JsonLd.filter(EDC_NS + "id", assetId))));
        Response response = call(StepName.CATALOG, () -> api.post("/v3/catalog/request", body));
        if (!response.ok()) {
            throw new DataspaceException(
                    StepName.CATALOG,
                    "Catalog request to " + partner.bpnl() + " failed: HTTP " + response.status() + " "
                            + abbreviate(response.raw()),
                    true,
                    response.status(),
                    null);
        }
        for (JsonNode dataset : JsonLd.list(response.body(), "dataset")) {
            if (!assetId.equals(JsonLd.scalar(dataset.get("@id")))) {
                continue;
            }
            List<JsonNode> offers = JsonLd.list(dataset, "hasPolicy");
            if (!offers.isEmpty()) {
                JsonNode offer = offers.getFirst();
                @SuppressWarnings("unchecked")
                Map<String, Object> policy = ManagementApi.JSON.convertValue(offer, Map.class);
                return new CatalogOffer(assetId, JsonLd.scalar(offer.get("@id")), policy);
            }
        }
        throw new DataspaceException(
                StepName.CATALOG,
                "Asset " + assetId + " is not offered to us by " + partner.bpnl()
                        + " (not provisioned yet or we are not in the partner's access group)");
    }

    @Override
    public List<CatalogOffer> requestCatalogByType(PartnerEndpoint partner, String dctType) {
        String expanded = expandTaxonomy(dctType);
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.edcContext());
        body.put("@type", "CatalogRequest");
        body.put("counterPartyAddress", partner.dspEndpoint());
        body.put("counterPartyId", partner.did());
        body.put("protocol", JsonLd.DSP_PROTOCOL);
        // Digital Twin KIT: filter on the asset property dct:type (object with @id)
        body.put("querySpec", Map.of("filterExpression", List.of(JsonLd.filter("'" + DCT_TYPE + "'.'@id'", expanded))));
        Response response = call(StepName.CATALOG, () -> api.post("/v3/catalog/request", body));
        if (!response.ok()) {
            throw new DataspaceException(
                    StepName.CATALOG,
                    "Catalog request to " + partner.bpnl() + " failed: HTTP " + response.status() + " "
                            + abbreviate(response.raw()),
                    true,
                    response.status(),
                    null);
        }
        List<CatalogOffer> offers = new ArrayList<>();
        for (JsonNode dataset : JsonLd.list(response.body(), "dataset")) {
            String type = datasetType(dataset);
            if (type != null && !type.equals(expanded) && !type.equals(dctType)) {
                continue;
            }
            List<JsonNode> policies = JsonLd.list(dataset, "hasPolicy");
            if (!policies.isEmpty()) {
                JsonNode offer = policies.getFirst();
                @SuppressWarnings("unchecked")
                Map<String, Object> policy = ManagementApi.JSON.convertValue(offer, Map.class);
                offers.add(
                        new CatalogOffer(JsonLd.scalar(dataset.get("@id")), JsonLd.scalar(offer.get("@id")), policy));
            }
        }
        return offers;
    }

    /** {@code cx-taxo:X} → {@code https://w3id.org/catenax/taxonomy#X}. */
    static String expandTaxonomy(String type) {
        return type.startsWith("cx-taxo:") ? CX_TAXO_NS + type.substring("cx-taxo:".length()) : type;
    }

    private static String datasetType(JsonNode dataset) {
        for (String name : List.of("dct:type", DCT_TYPE, "type")) {
            JsonNode value = dataset.get(name);
            if (value != null && !value.isNull()) {
                String type = JsonLd.scalar(value);
                return type == null ? null : expandTaxonomy(type);
            }
        }
        return null;
    }

    @Override
    public EndpointDataReference obtainEndpoint(PartnerEndpoint partner, CatalogOffer offer, StepListener listener) {
        Optional<EndpointDataReference> cached = cachedEndpoint(partner, offer.assetId());
        if (cached.isPresent()) {
            reportCached(cached.get(), offer.assetId(), listener);
            return cached.get();
        }
        return negotiate(partner, offer.assetId(), offer, listener);
    }

    @Override
    public EndpointDataReference obtainEndpoint(PartnerEndpoint partner, String assetId, StepListener listener) {
        Optional<EndpointDataReference> cached = cachedEndpoint(partner, assetId);
        if (cached.isPresent()) {
            reportCached(cached.get(), assetId, listener);
            return cached.get();
        }
        return negotiate(partner, assetId, null, listener);
    }

    private static void reportCached(EndpointDataReference edr, String assetId, StepListener listener) {
        var details = Map.<String, Object>of(
                "assetId", assetId, "agreementId", nullToEmpty(edr.agreementId()), "cachedEdr", true);
        listener.onStep(StepName.CATALOG, StepStatus.SKIPPED, Duration.ZERO, details);
        listener.onStep(StepName.NEGOTIATION, StepStatus.SKIPPED, Duration.ZERO, details);
        listener.onStep(
                StepName.TRANSFER,
                StepStatus.SKIPPED,
                Duration.ZERO,
                Map.of("transferProcessId", nullToEmpty(edr.transferProcessId()), "cachedEdr", true));
        listener.onStep(StepName.EDR, StepStatus.OK, Duration.ZERO, edrDetails(edr));
    }

    private EndpointDataReference negotiate(
            PartnerEndpoint partner, String assetId, CatalogOffer known, StepListener listener) {
        long start = System.nanoTime();
        CatalogOffer offer = known;
        if (offer == null) {
            try {
                offer = requestCatalog(partner, assetId);
            } catch (DataspaceException ex) {
                listener.onStep(StepName.CATALOG, StepStatus.FAILED, elapsed(start), error(ex, assetId));
                throw ex;
            }
            listener.onStep(
                    StepName.CATALOG,
                    StepStatus.OK,
                    elapsed(start),
                    Map.of("assetId", assetId, "offerId", nullToEmpty(offer.offerId())));
        }

        long negotiationStart = System.nanoTime();
        var policy = new LinkedHashMap<>(offer.policy());
        policy.remove("@context");
        policy.put("assigner", partner.did());
        policy.put("target", assetId);
        var request = new LinkedHashMap<String, Object>();
        request.put("@context", JsonLd.policyContext());
        request.put("@type", "ContractRequest");
        request.put("counterPartyAddress", partner.dspEndpoint());
        request.put("protocol", JsonLd.DSP_PROTOCOL);
        request.put("policy", policy);
        Response created = call(StepName.NEGOTIATION, () -> api.post("/v3/edrs", request));
        String negotiationId = created.ok() ? JsonLd.scalar(created.body().get("@id")) : null;
        if (negotiationId == null) {
            var ex = new DataspaceException(
                    StepName.NEGOTIATION,
                    "Contract request failed: HTTP " + created.status() + " " + abbreviate(created.raw()),
                    true,
                    created.status(),
                    null);
            listener.onStep(StepName.NEGOTIATION, StepStatus.FAILED, elapsed(negotiationStart), error(ex, assetId));
            throw ex;
        }

        long deadline = System.nanoTime() + properties.negotiationTimeout().toNanos();
        String agreementId = null;
        boolean negotiationReported = false;
        long transferStart = 0;
        int polls = 0;
        while (System.nanoTime() < deadline) {
            Optional<JsonNode> entry = edrEntries(JsonLd.querySpec("contractNegotiationId", negotiationId, 5)).stream()
                    .findFirst();
            if (entry.isPresent()) {
                String transferProcessId = JsonLd.text(entry.get(), "transferProcessId");
                agreementId = Optional.ofNullable(JsonLd.text(entry.get(), "agreementId"))
                        .orElse(agreementId);
                if (!negotiationReported) {
                    listener.onStep(
                            StepName.NEGOTIATION,
                            StepStatus.OK,
                            elapsed(negotiationStart),
                            Map.of("negotiationId", negotiationId, "agreementId", nullToEmpty(agreementId)));
                    transferStart = negotiationStart;
                }
                listener.onStep(
                        StepName.TRANSFER,
                        StepStatus.OK,
                        elapsed(transferStart == 0 ? negotiationStart : transferStart),
                        Map.of("transferProcessId", nullToEmpty(transferProcessId)));
                long edrStart = System.nanoTime();
                EndpointDataReference edr;
                try {
                    edr = dataAddress(transferProcessId, agreementId, false)
                            .orElseThrow(() -> new DataspaceException(
                                    StepName.EDR, "No data address for transfer " + transferProcessId));
                } catch (DataspaceException ex) {
                    listener.onStep(StepName.EDR, StepStatus.FAILED, elapsed(edrStart), error(ex, assetId));
                    throw ex;
                }
                listener.onStep(StepName.EDR, StepStatus.OK, elapsed(edrStart), edrDetails(edr));
                return edr;
            }
            if (polls++ % 4 == 0) {
                Response negotiation = api.get("/v3/contractnegotiations/" + encode(negotiationId));
                String state = negotiation.ok() ? JsonLd.text(negotiation.body(), "state") : null;
                if ("TERMINATED".equals(state)) {
                    var ex = new DataspaceException(
                            StepName.NEGOTIATION,
                            "Negotiation " + negotiationId + " terminated: "
                                    + nullToEmpty(JsonLd.text(negotiation.body(), "errorDetail")),
                            true,
                            null,
                            null);
                    listener.onStep(
                            StepName.NEGOTIATION, StepStatus.FAILED, elapsed(negotiationStart), error(ex, assetId));
                    throw ex;
                }
                if ("FINALIZED".equals(state) && !negotiationReported) {
                    agreementId = JsonLd.text(negotiation.body(), "contractAgreementId");
                    listener.onStep(
                            StepName.NEGOTIATION,
                            StepStatus.OK,
                            elapsed(negotiationStart),
                            Map.of("negotiationId", negotiationId, "agreementId", nullToEmpty(agreementId)));
                    negotiationReported = true;
                    transferStart = System.nanoTime();
                }
                if (negotiationReported && agreementId != null) {
                    checkTransferNotTerminated(agreementId, transferStart, assetId, listener);
                }
            }
            sleep(properties.pollInterval());
        }
        StepName step = negotiationReported ? StepName.TRANSFER : StepName.NEGOTIATION;
        var ex = new DataspaceException(
                step,
                "No EDR within " + properties.negotiationTimeout() + " (negotiation " + negotiationId + ")",
                true,
                null,
                null);
        listener.onStep(step, StepStatus.FAILED, elapsed(negotiationStart), error(ex, assetId));
        throw ex;
    }

    private void checkTransferNotTerminated(
            String agreementId, long transferStart, String assetId, StepListener listener) {
        Response transfers = api.post("/v3/transferprocesses/request", JsonLd.querySpec("contractId", agreementId, 5));
        if (!transfers.ok()) {
            return;
        }
        for (JsonNode transfer : JsonLd.list(transfers.body(), null)) {
            if ("TERMINATED".equals(JsonLd.text(transfer, "state"))) {
                var ex = new DataspaceException(
                        StepName.TRANSFER,
                        "Transfer " + JsonLd.scalar(transfer.get("@id")) + " terminated: "
                                + nullToEmpty(JsonLd.text(transfer, "errorDetail")),
                        true,
                        null,
                        null);
                listener.onStep(StepName.TRANSFER, StepStatus.FAILED, elapsed(transferStart), error(ex, assetId));
                throw ex;
            }
        }
    }

    /** Newest stored EDR of the provider's asset whose data address can be resolved. */
    private Optional<EndpointDataReference> cachedEndpoint(PartnerEndpoint partner, String assetId) {
        for (JsonNode entry : providerEntries(partner, assetId)) {
            String transferProcessId = JsonLd.text(entry, "transferProcessId");
            try {
                Optional<EndpointDataReference> edr =
                        dataAddress(transferProcessId, JsonLd.text(entry, "agreementId"), true);
                if (edr.isPresent()) {
                    return edr;
                }
            } catch (DataspaceException ex) {
                LOG.debug("Stored EDR {} not usable: {}", transferProcessId, ex.getMessage());
            }
            // stale entry (e.g. transfer terminated on the provider side): remove so it is not tried again
            api.delete("/v3/edrs/" + encode(transferProcessId));
        }
        return Optional.empty();
    }

    private List<JsonNode> providerEntries(PartnerEndpoint partner, String assetId) {
        var query = new LinkedHashMap<String, Object>();
        query.put("@context", JsonLd.edcContext());
        query.put("@type", "QuerySpec");
        query.put("limit", 20);
        query.put(
                "filterExpression",
                List.of(JsonLd.filter("assetId", assetId), JsonLd.filter("providerId", partner.did())));
        List<JsonNode> entries = new ArrayList<>(edrEntries(query));
        entries.sort(Comparator.comparingLong((JsonNode e) -> {
                    String createdAt = JsonLd.text(e, "createdAt");
                    return createdAt == null ? 0L : Long.parseLong(createdAt);
                })
                .reversed());
        return entries;
    }

    private List<JsonNode> edrEntries(Map<String, Object> query) {
        Response response = call(StepName.EDR, () -> api.post("/v3/edrs/request", query));
        if (!response.ok()) {
            throw new DataspaceException(
                    StepName.EDR, "EDR query failed: HTTP " + response.status(), true, response.status(), null);
        }
        return JsonLd.list(response.body(), null);
    }

    private Optional<EndpointDataReference> dataAddress(String transferProcessId, String agreementId, boolean cached) {
        Response response = call(
                StepName.EDR,
                () -> api.get("/v3/edrs/" + encode(transferProcessId) + "/dataaddress?auto_refresh=true"));
        if (response.status() == 404) {
            return Optional.empty();
        }
        if (!response.ok()) {
            throw new DataspaceException(
                    StepName.EDR,
                    "Data address of " + transferProcessId + ": HTTP " + response.status() + " "
                            + abbreviate(response.raw()),
                    true,
                    response.status(),
                    null);
        }
        String endpoint = JsonLd.text(response.body(), "endpoint");
        String authorization = JsonLd.text(response.body(), "authorization");
        if (endpoint == null || authorization == null) {
            return Optional.empty();
        }
        return Optional.of(
                new EndpointDataReference(transferProcessId, agreementId, URI.create(endpoint), authorization, cached));
    }

    @Override
    public void invalidateEndpoint(PartnerEndpoint partner, String assetId) {
        try {
            for (JsonNode entry : providerEntries(partner, assetId)) {
                api.delete("/v3/edrs/" + encode(JsonLd.text(entry, "transferProcessId")));
            }
        } catch (RuntimeException ex) {
            LOG.warn("Cannot invalidate EDRs of {} / {}: {}", partner.bpnl(), assetId, ex.toString());
        }
    }

    @Override
    public DataPlaneResponse call(
            EndpointDataReference edr,
            String method,
            String pathAndQuery,
            byte[] body,
            String contentType,
            Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(resolve(edr, pathAndQuery))
                .timeout(properties.requestTimeout())
                .header("Authorization", edr.authorization())
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        headers.forEach(request::header);
        try {
            HttpResponse<byte[]> response = dataPlane.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new DataPlaneResponse(response.statusCode(), response.body(), firstValues(response));
        } catch (IOException ex) {
            throw new DataspaceException(
                    StepName.DELIVERY,
                    "Data plane " + edr.endpoint() + " not reachable: " + ManagementApi.describe(ex),
                    true,
                    null,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataspaceException(StepName.DELIVERY, "interrupted", true, null, ex);
        }
    }

    @Override
    public DataPlaneStream stream(EndpointDataReference edr, String pathAndQuery, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(resolve(edr, pathAndQuery))
                .timeout(properties.negotiationTimeout())
                .header("Authorization", edr.authorization())
                .GET();
        headers.forEach(request::header);
        try {
            HttpResponse<java.io.InputStream> response =
                    dataPlane.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            return new DataPlaneStream(response.statusCode(), response.body(), length, firstValues(response));
        } catch (IOException ex) {
            throw new DataspaceException(
                    StepName.DOCUMENT_FETCH,
                    "Data plane " + edr.endpoint() + " not reachable: " + ManagementApi.describe(ex),
                    true,
                    null,
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataspaceException(StepName.DOCUMENT_FETCH, "interrupted", true, null, ex);
        }
    }

    /** EDR endpoint plus path/query (no double slashes). */
    static URI resolve(EndpointDataReference edr, String pathAndQuery) {
        if (pathAndQuery == null || pathAndQuery.isEmpty()) {
            return edr.endpoint();
        }
        String base = edr.endpoint().toString().replaceAll("/+$", "");
        String suffix =
                pathAndQuery.startsWith("/") || pathAndQuery.startsWith("?") ? pathAndQuery : "/" + pathAndQuery;
        return URI.create(base + suffix);
    }

    private static Map<String, String> firstValues(HttpResponse<?> response) {
        var headers = new LinkedHashMap<String, String>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.getFirst());
            }
        });
        return headers;
    }

    // ---------------------------------------------------------------------------------------- reconciliation

    @Override
    public List<String> listContractDefinitionIds() {
        Map<String, Object> spec = new LinkedHashMap<>(JsonLd.querySpec(null, null, 1000));
        Response response = call(StepName.CATALOG, () -> api.post("/v3/contractdefinitions/request", spec));
        if (!response.ok()) {
            throw provisioningFailure("list contract definitions", response);
        }
        return JsonLd.list(response.body(), null).stream()
                .map(n -> JsonLd.scalar(n.get("@id")))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public void removeOffer(String contractDefinitionId, String assetId) {
        Response definition = api.delete("/v3/contractdefinitions/" + encode(contractDefinitionId));
        if (!definition.ok() && definition.status() != 404) {
            throw provisioningFailure("delete contract definition " + contractDefinitionId, definition);
        }
        Response asset = api.delete("/v3/assets/" + encode(assetId));
        if (!asset.ok() && asset.status() != 404) {
            // assets with agreements cannot be deleted (409); the offer is gone with the contract definition
            LOG.info("EDC: asset {} kept: HTTP {}", assetId, asset.status());
        }
    }

    @Override
    public boolean removePolicy(String policyId) {
        try {
            Response response = api.delete("/v3/policydefinitions/" + encode(policyId));
            return response.ok();
        } catch (RuntimeException ex) {
            LOG.debug("EDC: policy {} not removed: {}", policyId, ex.toString());
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------- monitoring

    @Override
    public List<Map<String, Object>> listNegotiations(int limit) {
        return listNegotiations(null, 0, limit);
    }

    @Override
    public List<Map<String, Object>> listTransfers(int limit) {
        return listTransfers(null, 0, limit);
    }

    @Override
    public List<Map<String, Object>> listNegotiations(String state, int offset, int limit) {
        return query("/v3/contractnegotiations/request", state, offset, limit, true);
    }

    @Override
    public List<Map<String, Object>> listTransfers(String state, int offset, int limit) {
        return query("/v3/transferprocesses/request", state, offset, limit, true);
    }

    @Override
    public List<Map<String, Object>> listAssets(int limit) {
        return query(ASSET_QUERY_PATH, null, 0, limit, false);
    }

    @Override
    public List<Map<String, Object>> listPolicies(int limit) {
        return query("/v3/policydefinitions/request", null, 0, limit, false);
    }

    @Override
    public List<Map<String, Object>> listContractDefinitions(int limit) {
        return query("/v3/contractdefinitions/request", null, 0, limit, false);
    }

    @Override
    public List<CatalogDataset> requestFullCatalog(PartnerEndpoint partner) {
        var body = new LinkedHashMap<String, Object>();
        body.put("@context", JsonLd.edcContext());
        body.put("@type", "CatalogRequest");
        body.put("counterPartyAddress", partner.dspEndpoint());
        body.put("counterPartyId", partner.did());
        body.put("protocol", JsonLd.DSP_PROTOCOL);
        body.put("querySpec", Map.of("offset", 0, "limit", 500));
        Response response = call(StepName.CATALOG, () -> api.post("/v3/catalog/request", body));
        if (!response.ok()) {
            throw new DataspaceException(
                    StepName.CATALOG,
                    "Catalog request to " + partner.bpnl() + " failed: HTTP " + response.status() + " "
                            + abbreviate(response.raw()),
                    true,
                    response.status(),
                    null);
        }
        List<CatalogDataset> datasets = new ArrayList<>();
        for (JsonNode dataset : JsonLd.list(response.body(), "dataset")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = new LinkedHashMap<>(ManagementApi.JSON.convertValue(dataset, Map.class));
            properties.remove("hasPolicy");
            properties.remove("distribution");
            properties.remove("@context");
            List<Map<String, Object>> offers = new ArrayList<>();
            for (JsonNode offer : JsonLd.list(dataset, "hasPolicy")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> policy = ManagementApi.JSON.convertValue(offer, Map.class);
                offers.add(policy);
            }
            datasets.add(
                    new CatalogDataset(JsonLd.scalar(dataset.get("@id")), datasetType(dataset), properties, offers));
        }
        return datasets;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String path, String state, int offset, int limit, boolean newestFirst) {
        Map<String, Object> spec = new LinkedHashMap<>(JsonLd.querySpec(null, null, limit));
        spec.put("offset", Math.max(0, offset));
        if (newestFirst) {
            spec.put("sortField", "createdAt");
            spec.put("sortOrder", "DESC");
        }
        if (state != null && !state.isBlank()) {
            spec.put("filterExpression", List.of(JsonLd.filter("state", state.strip())));
        }
        Response response = call(StepName.CATALOG, () -> api.post(path, spec));
        if (!response.ok()) {
            throw new IllegalStateException("EDC query " + path + " failed: HTTP " + response.status());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode node : JsonLd.list(response.body(), null)) {
            if (node instanceof ObjectNode object) {
                object.remove("@context");
            }
            out.add(ManagementApi.JSON.convertValue(node, Map.class));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private static Response call(StepName step, java.util.function.Supplier<Response> request) {
        try {
            return request.get();
        } catch (ManagementApiUnavailable ex) {
            throw new DataspaceException(step, "EDC management API not reachable: " + ex.getMessage(), true, null, ex);
        }
    }

    private static Map<String, Object> edrDetails(EndpointDataReference edr) {
        var details = new LinkedHashMap<String, Object>();
        details.put("transferProcessId", nullToEmpty(edr.transferProcessId()));
        details.put("agreementId", nullToEmpty(edr.agreementId()));
        details.put("endpoint", edr.endpoint().toString());
        details.put("cachedEdr", edr.cached());
        return details;
    }

    private static Map<String, Object> error(DataspaceException ex, String assetId) {
        var details = new LinkedHashMap<String, Object>();
        details.put("assetId", assetId);
        details.put("error", abbreviate(ex.getMessage()));
        if (ex.httpStatus() != null) {
            details.put("httpStatus", ex.httpStatus());
        }
        return details;
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataspaceException(StepName.NEGOTIATION, "interrupted", true, null, ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
