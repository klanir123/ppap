package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Business partner of the tenant. */
public record PartnerDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Roles of the partner from our point of view")
        List<PartnerRole> roles,

        String dspEndpoint,
        String did,
        String contactEmail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Active and DSP endpoint and DID set (usable for exchange)")
        boolean exchangeReady) {

    static PartnerDto from(Partner partner) {
        var v = partner.values();
        var m = partner.metadata();
        return new PartnerDto(
                partner.id(),
                v.bpnl().value(),
                v.name(),
                v.roles().stream().sorted().toList(),
                v.dspEndpoint() == null ? null : v.dspEndpoint().toString(),
                v.did(),
                v.contactEmail(),
                partner.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt(),
                partner.exchangeReady());
    }
}
