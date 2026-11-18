package com.msg.ppap.identity.adapter.in.rest;

import com.msg.ppap.identity.config.TenantProperties;
import com.msg.ppap.platform.security.OidcProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public bootstrap configuration for the SPA (participant identity, OIDC settings, version). */
@RestController
@Tag(name = "Identity")
public class ConfigController {

    static final String DEFAULT_LOCALE = "de";
    static final String UNKNOWN_VERSION = "dev";

    private final ConfigResponse response;

    public ConfigController(
            TenantProperties tenant, OidcProperties oidc, ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.response = new ConfigResponse(
                new ParticipantInfo(tenant.bpnl(), tenant.name(), tenant.roleLabel()),
                new OidcInfo(oidc.effectiveAuthority(), oidc.clientId()),
                DEFAULT_LOCALE,
                build != null && build.getVersion() != null ? build.getVersion() : UNKNOWN_VERSION);
    }

    @GetMapping("/api/v1/config")
    @SecurityRequirements // public
    @Operation(operationId = "getConfig", summary = "Public runtime configuration for the web UI")
    public ConfigResponse config() {
        return response;
    }
}
