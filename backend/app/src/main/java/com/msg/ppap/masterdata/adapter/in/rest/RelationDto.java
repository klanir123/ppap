package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.RelationView;
import com.msg.ppap.masterdata.domain.ExchangeMode;
import com.msg.ppap.masterdata.domain.PartnerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Customer-supplier relation from the tenant's point of view. */
public record RelationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerBpnl,
        @Schema(description = "Partner name (read-only)") String partnerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerRole partnerRole,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExchangeMode exchangeMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String policyProfile,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long mappingCount,
        Instant createdAt,
        Instant updatedAt,

        @Schema(
                description = "Effect on the offers of the own EDC (only when a write changed policyProfile or"
                        + " exchangeMode, otherwise null)")
        ReconciliationDto reconciliation) {

    static RelationDto from(RelationView view) {
        return from(view, null);
    }

    static RelationDto from(RelationView view, ReconciliationDto reconciliation) {
        var relation = view.relation();
        var v = relation.values();
        var m = relation.metadata();
        return new RelationDto(
                relation.id(),
                v.partnerBpnl().value(),
                view.partnerName(),
                v.partnerRole(),
                v.exchangeMode(),
                v.policyProfile(),
                relation.active(),
                m.version(),
                view.mappingCount(),
                m.createdAt(),
                m.updatedAt(),
                reconciliation);
    }
}
