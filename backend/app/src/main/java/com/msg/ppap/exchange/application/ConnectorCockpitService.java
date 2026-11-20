package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.CatalogDataset;
import com.msg.ppap.exchange.application.port.out.ComponentStatusProbe;
import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.config.DtrOfferProperties;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import com.msg.ppap.platform.domain.PpapException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Read side of the connector cockpit (UC-10, docs/api/cascade-transparency-api.md §2): everything is read live from
 * the own EDC management API through the {@link DataspaceConnector} port; nothing is written except via provisioning.
 */
@Service
public class ConnectorCockpitService {

    /** Problem code of a failed live catalog request (EDC message in {@code detail}). */
    public static final String CATALOG_REQUEST_FAILED = "CATALOG_REQUEST_FAILED";

    static final int LIST_LIMIT = 1000;
    static final String MANAGED_PREFIX = "ppap-";

    private final DataspaceConnector connector;
    private final ProvisioningService provisioning;
    private final ExchangeContext context;
    private final EdcProperties edc;
    private final DtrOfferProperties dtr;
    private final ComponentStatusProbe components;
    private final MasterdataQueries masterdata;
    private final PartnerDirectory partners;

    @SuppressWarnings("java:S107")
    public ConnectorCockpitService(
            DataspaceConnector connector,
            ProvisioningService provisioning,
            ExchangeContext context,
            EdcProperties edc,
            DtrOfferProperties dtr,
            ComponentStatusProbe components,
            MasterdataQueries masterdata,
            PartnerDirectory partners) {
        this.connector = connector;
        this.provisioning = provisioning;
        this.context = context;
        this.edc = edc;
        this.dtr = dtr;
        this.components = components;
        this.masterdata = masterdata;
        this.partners = partners;
    }

    /** Overview of connector, DTR, provisioning, outbox/inbox and EDC object counts. */
    public Overview overview() {
        ConnectorHealth health = connector.health();
        Map<String, Long> messages = context.store().countByDirectionAndStatus();
        Counts counts = null;
        if (health.reachable()) {
            counts = new Counts(
                    count(() -> connector.listAssets(LIST_LIMIT)),
                    count(() -> connector.listPolicies(LIST_LIMIT)),
                    count(() -> connector.listContractDefinitions(LIST_LIMIT)),
                    count(() -> connector.listNegotiations(null, 0, LIST_LIMIT)),
                    count(() -> connector.listTransfers(null, 0, LIST_LIMIT)));
        }
        String dtrStatus = components.status("dtr");
        return new Overview(
                new EdcInfo(
                        health.reachable() ? "UP" : "DOWN",
                        null,
                        edc.participantId(),
                        edc.dspUrl() == null ? null : edc.dspUrl().toString(),
                        health.error(),
                        health.responseTime().toMillis()),
                dtr.offered() || (dtrStatus != null && !"UNKNOWN".equals(dtrStatus))
                        ? new DtrInfo(dtrStatus == null ? "UNKNOWN" : dtrStatus, dtr.publicUrl())
                        : null,
                provisioning.state(),
                messages.getOrDefault(ExchangeQueryService.key(Direction.OUTBOUND, MessageStatus.PENDING), 0L)
                        + messages.getOrDefault(
                                ExchangeQueryService.key(Direction.OUTBOUND, MessageStatus.IN_PROGRESS), 0L),
                messages.getOrDefault(ExchangeQueryService.key(Direction.OUTBOUND, MessageStatus.FAILED), 0L),
                messages.getOrDefault(ExchangeQueryService.key(Direction.INBOUND, MessageStatus.REJECTED), 0L),
                counts);
    }

    /** EDC objects (assets, policies, contract definitions) with the flag "managed by the connector". */
    public List<EdcObject> assets() {
        return objects(connector.listAssets(LIST_LIMIT));
    }

    public List<EdcObject> policies() {
        return objects(connector.listPolicies(LIST_LIMIT));
    }

    public List<EdcObject> contractDefinitions() {
        return objects(connector.listContractDefinitions(LIST_LIMIT));
    }

    /** Contract negotiations (newest first) with the partner resolved from the master data. */
    public List<Process> negotiations(String state, int page, int size) {
        return processes(connector.listNegotiations(blankToNull(state), page * size, size), "contractAgreementId");
    }

    /** Transfer processes (newest first) with the partner resolved from the master data. */
    public List<Process> transfers(String state, int page, int size) {
        return processes(connector.listTransfers(blankToNull(state), page * size, size), "contractId");
    }

    /**
     * The partner's catalog, requested live via DSP (AK-10-2: no cache).
     *
     * @throws PpapException {@code 422 CATALOG_REQUEST_FAILED} with the EDC message, {@code 422
     *     PARTNER_NOT_EXCHANGE_READY} without DSP endpoint/DID
     */
    public PartnerCatalog catalog(String partnerBpnl) {
        PartnerEndpoint endpoint;
        try {
            endpoint = partners.endpoint(partnerBpnl, StepName.CATALOG);
        } catch (PartnerNotReadyException ex) {
            throw new PpapException(
                    PpapException.Kind.UNPROCESSABLE,
                    "PARTNER_NOT_EXCHANGE_READY",
                    "Partner " + partnerBpnl + " is unknown, inactive or has no DSP endpoint/DID");
        }
        long start = System.nanoTime();
        try {
            List<CatalogDataset> datasets = connector.requestFullCatalog(endpoint);
            return new PartnerCatalog(
                    endpoint.bpnl(),
                    partners.name(endpoint.bpnl()).orElse(null),
                    endpoint.did(),
                    endpoint.dspEndpoint(),
                    Duration.ofNanos(System.nanoTime() - start).toMillis(),
                    context.now(),
                    datasets);
        } catch (DataspaceException ex) {
            throw new PpapException(PpapException.Kind.UNPROCESSABLE, CATALOG_REQUEST_FAILED, ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private List<Process> processes(List<Map<String, Object>> raw, String agreementKey) {
        Map<String, Optional<PartnerInfo>> byDid = new HashMap<>();
        List<Process> result = new ArrayList<>();
        for (Map<String, Object> p : raw) {
            String did = str(p.get("counterPartyId"));
            String bpnl = bpnlOf(did);
            Optional<PartnerInfo> partner =
                    bpnl == null ? Optional.empty() : byDid.computeIfAbsent(did, d -> masterdata.findPartner(bpnl));
            result.add(new Process(
                    str(p.get("@id")),
                    str(p.get("type")),
                    str(p.get("state")),
                    did,
                    bpnl,
                    partner.map(PartnerInfo::name).orElse(null),
                    str(p.get("assetId")),
                    str(p.get(agreementKey)),
                    instant(p.get("createdAt")),
                    instant(p.containsKey("stateTimestamp") ? p.get("stateTimestamp") : p.get("updatedAt")),
                    str(p.get("errorDetail")),
                    p));
        }
        return result;
    }

    /** {@code did:web:<host>:<BPNL>} → BPNL (Catena-X convention of the local stack), else {@code null}. */
    static String bpnlOf(String did) {
        if (did == null) {
            return null;
        }
        String last = did.substring(did.lastIndexOf(':') + 1);
        return last.matches("^BPNL[0-9A-Z]{12}$") ? last : null;
    }

    private static List<EdcObject> objects(List<Map<String, Object>> raw) {
        return raw.stream()
                .map(o -> {
                    String id = str(o.get("@id"));
                    Map<String, Object> content = new LinkedHashMap<>(o);
                    content.remove("@context");
                    return new EdcObject(id, str(o.get("@type")), id != null && id.startsWith(MANAGED_PREFIX), content);
                })
                .toList();
    }

    private static int count(java.util.function.Supplier<List<Map<String, Object>>> query) {
        try {
            return query.get().size();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static Instant instant(Object value) {
        if (value instanceof Number millis) {
            return Instant.ofEpochMilli(millis.longValue());
        }
        if (value instanceof String text && text.matches("^[0-9]{10,}$")) {
            return Instant.ofEpochMilli(Long.parseLong(text));
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Cockpit overview. */
    public record Overview(
            EdcInfo edc,
            DtrInfo dtr,
            ProvisioningState provisioning,
            long outboxPending,
            long outboxFailed,
            long inboxRejected,
            Counts counts) {}

    /** Own connector. */
    public record EdcInfo(
            String status, String version, String participantId, String dspEndpoint, String error, long responseMs) {}

    /** Own Digital Twin Registry. */
    public record DtrInfo(String status, String url) {}

    /** Number of EDC objects ({@code -1} if a query failed). */
    public record Counts(int assets, int policies, int contractDefinitions, int negotiations, int transfers) {}

    /** EDC object (asset, policy definition, contract definition). */
    public record EdcObject(String id, String type, boolean managed, Map<String, Object> content) {}

    /** Contract negotiation or transfer process. */
    public record Process(
            String id,
            String type,
            String state,
            String counterPartyId,
            String partnerBpnl,
            String partnerName,
            String assetId,
            String agreementId,
            Instant createdAt,
            Instant updatedAt,
            String errorDetail,
            Map<String, Object> raw) {}

    /** Live catalog of a partner. */
    public record PartnerCatalog(
            String partnerBpnl,
            String partnerName,
            String partnerDid,
            String dspEndpoint,
            long durationMs,
            Instant requestedAt,
            List<CatalogDataset> datasets) {}
}
