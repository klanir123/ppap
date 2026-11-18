package com.msg.ppap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.platform.security.KeycloakRealmRoleConverter;
import com.msg.ppap.platform.web.CorrelationIdFilter;
import com.msg.ppap.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void configIsPublic() throws Exception {
        mvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.participant.bpnl").value("BPNL00000003AYRE"))
                .andExpect(jsonPath("$.participant.name").value("Test OEM AG"))
                .andExpect(jsonPath("$.participant.roleLabel").value("OEM"))
                .andExpect(jsonPath("$.oidc.authority").value("http://localhost:8180/realms/test"))
                .andExpect(jsonPath("$.oidc.clientId").value("ppap-ui"))
                .andExpect(jsonPath("$.defaultLocale").value("de"))
                .andExpect(jsonPath("$.version").isNotEmpty());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsUserFromToken() throws Exception {
        mvc.perform(get("/api/v1/me").with(keycloakUser("CUSTOMER_QUALITY_ENGINEER", "CONNECTOR_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.username").value("anna.qe"))
                .andExpect(jsonPath("$.displayName").value("Anna Quality"))
                .andExpect(jsonPath("$.email").value("anna@oem.example"))
                .andExpect(jsonPath("$.roles", Matchers.contains("CONNECTOR_ADMIN", "CUSTOMER_QUALITY_ENGINEER")))
                .andExpect(jsonPath("$.tenant.bpnl").value("BPNL00000003AYRE"));
    }

    @Test
    void echoesCorrelationId() throws Exception {
        mvc.perform(get("/api/v1/config").header(CorrelationIdFilter.HEADER, "test-corr-1"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-corr-1"));
    }

    @Test
    void unknownApiPathYieldsProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").with(keycloakUser()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void livenessIsUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessIsUpEvenThoughEdcIsDown() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void systemStatusAggregatesComponents() throws Exception {
        mvc.perform(get("/api/v1/system/status")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/system/status").with(keycloakUser("SUPPLIER_QUALITY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[*].name")
                        .value(Matchers.contains("database", "edc", "objectStorage", "dtr")))
                .andExpect(jsonPath("$.components[0].status").value("UP"))
                .andExpect(jsonPath("$.components[1].status").value("DOWN"))
                .andExpect(jsonPath("$.components[2].status").value("DOWN"))
                .andExpect(jsonPath("$.components[3].status").value("UNKNOWN"));
    }

    @Test
    void spaRoutesAreForwardedToIndex() throws Exception {
        mvc.perform(get("/cases/4711"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getForwardedUrl()).isEqualTo("/index.html"));
    }

    @Test
    void internalEdcPathIsNotYetServed() throws Exception {
        mvc.perform(get("/internal/edc/anything")).andExpect(status().isNotFound());
    }

    @Test
    void flywayMigrationsAreApplied() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_name in"
                                + " ('event_publication', 'event_publication_archive', 'audit_event', 'md_part',"
                                + " 'md_partner', 'md_site', 'md_relation', 'md_part_mapping', 'md_catalog_element',"
                                + " 'md_submission_level', 'md_submission_reason')",
                        Integer.class))
                .isEqualTo(11);
    }

    /** Simulates a Keycloak access token, mapped with the production role converter. */
    private static JwtRequestPostProcessor keycloakUser(String... roles) {
        return jwt().jwt(token -> token.subject("user-1")
                        .claim("preferred_username", "anna.qe")
                        .claim("name", "Anna Quality")
                        .claim("email", "anna@oem.example")
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }
}
