package com.msg.ppap.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.msg.ppap.platform.security.KeycloakRealmRoleConverter;
import java.util.List;
import java.util.Map;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Simulated Keycloak access tokens for MockMvc. */
public final class TestJwts {

    private TestJwts() {}

    /** User token with the given realm roles, mapped with the production converter. */
    public static JwtRequestPostProcessor user(String username, String... roles) {
        return jwt().jwt(token -> token.subject("sub-" + username)
                        .claim("preferred_username", username)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    /** Master data manager. */
    public static JwtRequestPostProcessor manager() {
        return user("mia.manager", "MASTERDATA_MANAGER");
    }

    /** Technical client (client credentials) with SYSTEM_INTEGRATION. */
    public static JwtRequestPostProcessor integration() {
        return jwt().jwt(token -> token.subject("sub-client")
                        .claim("preferred_username", "service-account-ppap-integration")
                        .claim("azp", "ppap-integration")
                        .claim("realm_access", Map.of("roles", List.of("SYSTEM_INTEGRATION"))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    /** Read-only quality engineer. */
    public static JwtRequestPostProcessor engineer() {
        return user("eric.engineer", "CUSTOMER_QUALITY_ENGINEER");
    }

    /** Connector admin. */
    public static JwtRequestPostProcessor admin() {
        return user("ada.admin", "CONNECTOR_ADMIN");
    }
}
