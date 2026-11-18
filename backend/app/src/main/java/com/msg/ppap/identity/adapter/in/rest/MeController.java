package com.msg.ppap.identity.adapter.in.rest;

import com.msg.ppap.identity.config.TenantProperties;
import com.msg.ppap.platform.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Information about the authenticated user, derived from the access token. */
@RestController
@Tag(name = "Identity")
public class MeController {

    private final TenantInfo tenant;

    public MeController(TenantProperties tenantProperties) {
        this.tenant = new TenantInfo(tenantProperties.bpnl(), tenantProperties.name(), tenantProperties.roleLabel());
    }

    @GetMapping("/api/v1/me")
    @Operation(operationId = "getMe", summary = "The authenticated user and tenant")
    public MeResponse me(JwtAuthenticationToken authentication) {
        var jwt = authentication.getToken();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(Roles.AUTHORITY_PREFIX))
                .map(authority -> authority.substring(Roles.AUTHORITY_PREFIX.length()))
                .sorted()
                .toList();
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"),
                roles,
                tenant);
    }
}
