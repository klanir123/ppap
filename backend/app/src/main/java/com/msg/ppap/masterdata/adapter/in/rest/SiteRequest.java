package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.SiteCommand;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create or replace a site.
 *
 * @param ownerBpnl own tenant or a known partner
 * @param active {@code null} keeps the current state (new sites are active)
 */
public record SiteRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpns,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ownerBpnl,
        String street,
        String postalCode,
        String city,
        @Schema(description = "ISO 3166-1 alpha-2") String country,
        Boolean active) {

    SiteCommand toCommand() {
        return new SiteCommand(bpns, name, ownerBpnl, street, postalCode, city, country, active);
    }
}
