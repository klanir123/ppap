package com.msg.ppap.identity.config;

import com.msg.ppap.platform.domain.Bpnl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identity of the participant (tenant) this connector instance belongs to. One tenant per instance in the
 * prototype (ADR-0004).
 *
 * @param bpnl business partner number ({@code PPAP_TENANT_BPNL})
 * @param name display name of the company ({@code PPAP_TENANT_NAME})
 * @param roleLabel role in the supply chain shown in the UI, e.g. "OEM" ({@code PPAP_TENANT_ROLE_LABEL})
 * @param did decentralized identifier of the participant ({@code PPAP_TENANT_DID})
 */
@Validated
@ConfigurationProperties("ppap.tenant")
public record TenantProperties(
        @NotBlank @Pattern(regexp = Bpnl.PATTERN, message = "must be a valid BPNL (" + Bpnl.PATTERN + ")")
        String bpnl,

        @NotBlank String name,
        @NotBlank String roleLabel,
        @Nullable String did) {

    /** The tenant's BPNL as value object. */
    public Bpnl businessPartnerNumber() {
        return new Bpnl(bpnl);
    }
}
