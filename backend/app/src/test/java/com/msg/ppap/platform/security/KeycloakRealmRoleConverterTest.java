package com.msg.ppap.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToRoleAuthorities() {
        Jwt jwt = jwt(Map.of(
                "realm_access",
                Map.of("roles", List.of(Roles.CUSTOMER_QUALITY_ENGINEER, Roles.CONNECTOR_ADMIN, "default-roles-oem"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER_QUALITY_ENGINEER", "ROLE_CONNECTOR_ADMIN", "ROLE_default-roles-oem");
    }

    @Test
    void ignoresDuplicatesAndBlankRoles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("SYSTEM_INTEGRATION", "", "SYSTEM_INTEGRATION"))));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SYSTEM_INTEGRATION");
    }

    @Test
    void returnsNoAuthoritiesWithoutRealmAccess() {
        assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    void returnsNoAuthoritiesForMalformedClaim() {
        assertThat(converter.convert(jwt(Map.of("realm_access", "not-a-map")))).isEmpty();
        assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", "not-a-list")))))
                .isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
