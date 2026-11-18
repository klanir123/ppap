package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.SiteView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Site of the tenant or of a partner. */
public record SiteDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ownerBpnl,

        @Schema(description = "Name of the owning tenant or partner (read-only)")
        String ownerName,

        String street,
        String postalCode,
        String city,
        String country,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt) {

    static SiteDto from(SiteView view) {
        var site = view.site();
        var v = site.values();
        var m = site.metadata();
        return new SiteDto(
                site.id(),
                v.bpns().value(),
                v.name(),
                v.ownerBpnl().value(),
                view.ownerName(),
                v.street(),
                v.postalCode(),
                v.city(),
                v.country(),
                site.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt());
    }
}
