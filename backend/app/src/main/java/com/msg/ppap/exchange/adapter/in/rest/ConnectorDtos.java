package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.application.ConnectorCockpitService;
import com.msg.ppap.exchange.application.DataspaceService;
import com.msg.ppap.exchange.application.PolicyPreviewService;
import com.msg.ppap.exchange.application.port.out.CatalogDataset;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** REST DTOs of the connector cockpit, policy preview and dataspace view (docs/api/cascade-transparency-api.md). */
final class ConnectorDtos {

    private ConnectorDtos() {}

    record ConnectorOverviewDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            EdcDto edc,

            @Schema(description = "Own Digital Twin Registry, null without DTR")
            DtrDto dtr,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ProvisioningStateDto provisioning,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ExchangeDtos.OutboxDto outbox,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ExchangeDtos.InboxDto inbox,

            @Schema(description = "Number of EDC objects (-1 if a query failed), null if the EDC is down")
            CountsDto counts) {}

    record EdcDto(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"UP", "DOWN"})
            String status,

            String version,
            String participantId,
            String dspEndpoint,
            String error,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long responseMs) {}

    record DtrDto(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"UP", "DOWN", "UNKNOWN"})
            String status,

            String url) {}

    record ProvisioningStateDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean ok,

            Instant lastRun,
            String error,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> assets,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "BPNLs in the access group")
            List<String> partners,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Usage policy id per offered asset")
            Map<String, String> usagePolicies) {}

    record CountsDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int assets,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int policies,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int contractDefinitions,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int negotiations,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int transfers) {}

    /** Asset, policy definition or contract definition of the own EDC. */
    record EdcObjectDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,

            String type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Provisioned by the connector (ppap-*)")
            boolean managed,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Content as JSON-LD (compacted)")
            Map<String, Object> content) {}

    /** Contract negotiation or transfer process. */
    record EdcProcessDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,

            @Schema(description = "CONSUMER or PROVIDER") String type,
            String state,

            @Schema(description = "Participant id (DID) of the partner")
            String counterPartyId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ConnectorPartnerDto partner,

            String assetId,
            String agreementId,
            Instant createdAt,
            Instant updatedAt,
            String errorDetail) {}

    record ConnectorPartnerDto(String bpnl, String name) {}

    record CatalogRequestDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String partnerBpnl) {}

    record CatalogDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ConnectorPartnerDto partner,

            String partnerDid,
            String dspEndpoint,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Duration of the DSP request")
            long durationMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant requestedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<CatalogDatasetDto> datasets) {}

    record CatalogDatasetDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String assetId,

            @Schema(description = "dct:type (expanded)") String type,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Map<String, Object> properties,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ODRL offers (JSON-LD, compacted)")
            List<Map<String, Object>> offers) {}

    record PolicyPreviewDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID profileId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long version,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Relations using the profile")
            List<String> relations,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<GeneratedPolicyDto> policies) {}

    record GeneratedPolicyDto(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"USAGE", "ACCESS_PARTNER", "ACCESS_SHARED"})
            String role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String policyId,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "PolicyDefinition as sent to the EDC management API (JSON-LD)")
            Map<String, Object> policy) {}

    record TopologyDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            SelfDto self,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<PartnerNodeDto> partners) {}

    record SelfDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String bpnl,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            String roleLabel,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ConnectorNodeDto connector) {}

    record ConnectorNodeDto(
            String participantId,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"UP", "DOWN"})
            String status) {}

    record PartnerNodeDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String bpnl,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "CUSTOMER and/or SUPPLIER")
            List<String> roles,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean exchangeReady,

            String did,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Primary relation (customer first)")
            RelationEdgeDto relation,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<RelationEdgeDto> relations) {}

    record RelationEdgeDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String partnerRole,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String exchangeMode,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String policyProfile,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean active) {}

    /** Lean exchanged message for the dataspace animation (all roles). */
    record RecentMessageDto(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Message id of the exchange record – the same identifier as messageId in the SSE"
                            + " events exchange.changed and connector.log (de-duplication)")
            UUID id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Technical id of the exchange record")
            UUID recordId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant at,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String direction,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String messageType,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ConnectorPartnerDto partner,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status,

            UUID caseId,
            String caseNumber,
            Instant completedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Steps (catalog → contract → EDR → …)")
            List<RecentStepDto> steps) {}

    record RecentStepDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String step,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant at,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long durationMs) {}

    static ConnectorOverviewDto overview(ConnectorCockpitService.Overview o) {
        var p = o.provisioning();
        var c = o.counts();
        return new ConnectorOverviewDto(
                new EdcDto(
                        o.edc().status(),
                        o.edc().version(),
                        o.edc().participantId(),
                        o.edc().dspEndpoint(),
                        o.edc().error(),
                        o.edc().responseMs()),
                o.dtr() == null ? null : new DtrDto(o.dtr().status(), o.dtr().url()),
                new ProvisioningStateDto(p.ok(), p.lastRun(), p.error(), p.assets(), p.partners(), p.usagePolicies()),
                new ExchangeDtos.OutboxDto(o.outboxPending(), o.outboxFailed()),
                new ExchangeDtos.InboxDto(o.inboxRejected()),
                c == null
                        ? null
                        : new CountsDto(
                                c.assets(), c.policies(), c.contractDefinitions(), c.negotiations(), c.transfers()));
    }

    static EdcObjectDto object(ConnectorCockpitService.EdcObject o) {
        return new EdcObjectDto(o.id(), o.type(), o.managed(), o.content());
    }

    static EdcProcessDto process(ConnectorCockpitService.Process p) {
        return new EdcProcessDto(
                p.id(),
                p.type(),
                p.state(),
                p.counterPartyId(),
                new ConnectorPartnerDto(p.partnerBpnl(), p.partnerName()),
                p.assetId(),
                p.agreementId(),
                p.createdAt(),
                p.updatedAt(),
                p.errorDetail());
    }

    static CatalogDto catalog(ConnectorCockpitService.PartnerCatalog c) {
        return new CatalogDto(
                new ConnectorPartnerDto(c.partnerBpnl(), c.partnerName()),
                c.partnerDid(),
                c.dspEndpoint(),
                c.durationMs(),
                c.requestedAt(),
                c.datasets().stream().map(ConnectorDtos::dataset).toList());
    }

    static CatalogDatasetDto dataset(CatalogDataset d) {
        return new CatalogDatasetDto(d.assetId(), d.type(), d.properties(), d.offers());
    }

    static PolicyPreviewDto preview(PolicyPreviewService.Preview p) {
        return new PolicyPreviewDto(
                p.profileId(),
                p.code(),
                p.version(),
                p.relations(),
                p.policies().stream()
                        .map(g -> new GeneratedPolicyDto(g.role(), g.policyId(), g.policy()))
                        .toList());
    }

    static TopologyDto topology(DataspaceService.Topology t) {
        return new TopologyDto(
                new SelfDto(
                        t.self().bpnl(),
                        t.self().name(),
                        t.self().roleLabel(),
                        new ConnectorNodeDto(
                                t.self().connector().participantId(),
                                t.self().connector().status())),
                t.partners().stream()
                        .map(p -> new PartnerNodeDto(
                                p.bpnl(),
                                p.name(),
                                p.roles(),
                                p.exchangeReady(),
                                p.did(),
                                edge(p.relation()),
                                p.relations().stream().map(ConnectorDtos::edge).toList()))
                        .toList());
    }

    static RelationEdgeDto edge(DataspaceService.RelationEdge e) {
        return new RelationEdgeDto(e.partnerRole(), e.exchangeMode(), e.policyProfile(), e.active());
    }

    static RecentMessageDto recent(ExchangeRecord r) {
        return new RecentMessageDto(
                r.messageId() == null ? r.id() : r.messageId(),
                r.id(),
                r.createdAt(),
                r.direction().name(),
                r.messageType(),
                new ConnectorPartnerDto(r.partnerBpnl(), r.partnerName()),
                r.status().name(),
                r.caseId(),
                r.caseNumber(),
                r.completedAt(),
                r.steps().stream().map(ConnectorDtos::step).toList());
    }

    static RecentStepDto step(ExchangeStep s) {
        return new RecentStepDto(s.step().name(), s.status().name(), s.at(), s.durationMs());
    }
}
