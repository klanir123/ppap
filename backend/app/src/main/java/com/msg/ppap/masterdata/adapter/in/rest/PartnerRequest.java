package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartnerCommand;
import com.msg.ppap.masterdata.domain.PartnerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Create or replace a partner.
 *
 * @param active {@code null} keeps the current state (new partners are active)
 */
public record PartnerRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartnerRole> roles,
        String dspEndpoint,
        String did,
        String contactEmail,
        Boolean active) {

    PartnerCommand toCommand() {
        return new PartnerCommand(
                bpnl,
                name,
                roles == null
                        ? List.of()
                        : roles.stream()
                                .map(role -> role == null ? "" : role.name())
                                .toList(),
                dspEndpoint,
                did,
                contactEmail,
                active);
    }
}
