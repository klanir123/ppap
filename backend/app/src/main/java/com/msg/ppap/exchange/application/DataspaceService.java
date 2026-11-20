package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.RelationInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Dataspace view (demo mode, E5-06, docs/api/cascade-transparency-api.md §6): the topology from the perspective of
 * the own participant (own connector, partners with relations) and the latest exchanged messages for the animation.
 * Readable by every authenticated role; no cross-tenant view (data sovereignty).
 */
@Service
public class DataspaceService {

    static final Duration CONNECTOR_STATUS_TTL = Duration.ofSeconds(15);

    private final MasterdataQueries masterdata;
    private final DataspaceConnector connector;
    private final ExchangeContext context;
    private final TenantContext tenant;
    private final EdcProperties edc;
    private final String roleLabel;
    private final AtomicReference<CachedStatus> connectorStatus = new AtomicReference<>();

    public DataspaceService(
            MasterdataQueries masterdata,
            DataspaceConnector connector,
            ExchangeContext context,
            TenantContext tenant,
            EdcProperties edc,
            @org.springframework.beans.factory.annotation.Value("${ppap.tenant.role-label:}") String roleLabel) {
        this.masterdata = masterdata;
        this.connector = connector;
        this.context = context;
        this.tenant = tenant;
        this.edc = edc;
        this.roleLabel = roleLabel;
    }

    /** Topology: own participant in the centre, partners with relations around it. */
    public Topology topology() {
        Map<String, List<RelationInfo>> relationsByPartner = new LinkedHashMap<>();
        for (RelationInfo relation : masterdata.relations()) {
            relationsByPartner
                    .computeIfAbsent(relation.partnerBpnl(), b -> new ArrayList<>())
                    .add(relation);
        }
        List<PartnerNode> partners = new ArrayList<>();
        for (var entry : relationsByPartner.entrySet()) {
            PartnerInfo partner = masterdata.findPartner(entry.getKey()).orElse(null);
            List<RelationEdge> edges = entry.getValue().stream()
                    .sorted(Comparator.comparing(RelationInfo::partnerRole))
                    .map(r -> new RelationEdge(r.partnerRole(), r.exchangeMode(), r.policyProfile(), r.active()))
                    .toList();
            Set<String> roles = new TreeSet<>();
            edges.forEach(e -> roles.add(e.partnerRole()));
            partners.add(new PartnerNode(
                    entry.getKey(),
                    partner == null ? entry.getKey() : partner.name(),
                    List.copyOf(roles),
                    partner != null && partner.exchangeReady(),
                    partner == null ? null : partner.did(),
                    edges.getFirst(),
                    edges));
        }
        return new Topology(
                new SelfNode(
                        tenant.tenantId(),
                        tenant.tenantName(),
                        roleLabel == null || roleLabel.isBlank() ? null : roleLabel,
                        new ConnectorNode(edc.participantId(), connectorStatus())),
                partners);
    }

    /** Latest exchanged messages (newest first) without payload. */
    public List<ExchangeRecord> recent(int limit) {
        return context.store()
                .search(null, null, null, List.of(), PageRequest.of(0, Math.clamp(limit, 1, 200)))
                .getContent();
    }

    private String connectorStatus() {
        Instant now = context.now();
        CachedStatus cached = connectorStatus.get();
        if (cached != null && cached.at().plus(CONNECTOR_STATUS_TTL).isAfter(now)) {
            return cached.status();
        }
        String status = connector.health().reachable() ? "UP" : "DOWN";
        connectorStatus.set(new CachedStatus(status, now));
        return status;
    }

    private record CachedStatus(String status, Instant at) {}

    /** Topology of the own participant. */
    public record Topology(SelfNode self, List<PartnerNode> partners) {}

    /** The own participant. */
    public record SelfNode(String bpnl, String name, String roleLabel, ConnectorNode connector) {}

    /** The own connector. */
    public record ConnectorNode(String participantId, String status) {}

    /**
     * A partner with relation.
     *
     * @param relation primary relation (customer relation first)
     * @param relations all relations with this partner
     */
    public record PartnerNode(
            String bpnl,
            String name,
            List<String> roles,
            boolean exchangeReady,
            String did,
            RelationEdge relation,
            List<RelationEdge> relations) {}

    /** A relation (edge of the topology). */
    public record RelationEdge(String partnerRole, String exchangeMode, String policyProfile, boolean active) {}
}
