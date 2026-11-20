package com.msg.ppap.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.exchange.application.ProvisioningService;
import com.msg.ppap.exchange.application.port.out.CatalogDataset;
import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.ContractDefinitionSpec;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import com.msg.ppap.support.TestKeys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phase 6 with Testcontainers and a stubbed dataspace connector: policy profile CRUD and validation, relation
 * reference, ODRL preview and the reconciliation that applies a profile to the per-partner offers (UC-10, AK-10-3);
 * connector cockpit read APIs; dataspace topology and recent messages for every role.
 */
@TestPropertySource(
        properties = {
            "ppap.edc.callback-secret=test-callback-secret",
            "ppap.edc.callback-base-url=http://test-app:8080",
            "ppap.exchange.scheduling-enabled=false"
        })
class ConnectorAndPolicyProfileIntegrationTest extends AbstractIntegrationTest {

    static final String PROFILES = "/api/v1/policy-profiles";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ProvisioningService provisioning;

    @MockitoBean
    DataspaceConnector connector;

    String partner;

    @BeforeEach
    void setUp() throws Exception {
        when(connector.health()).thenReturn(ConnectorHealth.up("http://cp", Duration.ofMillis(5)));
        when(connector.ensureBusinessPartnerGroup(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        when(connector.renderPolicy(any())).thenAnswer(i -> Map.of("@id", ((PolicySpec) i.getArgument(0)).id()));
        partner = TestKeys.bpnl();
        create(
                "/api/v1/partners",
                Map.of(
                        "bpnl",
                        partner,
                        "name",
                        "Customer " + partner,
                        "roles",
                        List.of("CUSTOMER"),
                        "dspEndpoint",
                        "http://edc.example/api/v1/dsp",
                        "did",
                        "did:web:wallet-stub:" + partner),
                TestJwts.manager());
    }

    @Test
    void policyProfileCrudValidationAndDefaultProtection() throws Exception {
        JsonNode list = read(mvc.perform(get(PROFILES).with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode defaultProfile = LiveJson.find(list, "code", "default");
        assertThat(defaultProfile.get("usage").get("usagePurposes").get(0).asString())
                .isEqualTo("cx.quality.base:1");
        assertThat(defaultProfile.get("access").get("type").asString()).isEqualTo("BPN_GROUP");

        mvc.perform(post(PROFILES)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile("x-" + partner, "a:1"))))
                .andExpect(status().isForbidden());
        mvc.perform(post(PROFILES)
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile("bad", "no version"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("usage.usagePurposes[0]"));

        String code = "p-" + partner.toLowerCase();
        JsonNode created = create(PROFILES, profile(code, "msg.ppap.productApproval:1"), TestJwts.admin());
        String id = created.get("id").asString();
        assertThat(created.get("reconciliation").get("status").asString()).isEqualTo("APPLIED");
        assertThat(created.get("reconciliation").get("contractDefinitionIds").toString())
                .contains("ppap-notification-cd");
        mvc.perform(get(PROFILES + "/" + id).with(TestJwts.engineer()))
                .andExpect(jsonPath("$.reconciliation").doesNotExist());
        mvc.perform(post(PROFILES)
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile(code, "a:1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));

        // preview: usage and per-partner access policy as JSON-LD (rendered by the connector port)
        mvc.perform(get(PROFILES + "/" + id + "/preview").with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies[0].role").value("USAGE"))
                .andExpect(jsonPath("$.policies[0].policyId")
                        .value(org.hamcrest.Matchers.startsWith("ppap-usage-" + code + "-")))
                .andExpect(jsonPath("$.policies[1].role").value("ACCESS_PARTNER"));
        mvc.perform(get(PROFILES + "/" + defaultProfile.get("id").asString() + "/preview")
                        .with(TestJwts.engineer()))
                .andExpect(jsonPath("$.policies[2].role").value("ACCESS_SHARED"));

        // relation references profiles by code: unknown → 400, existing → ok, then the profile cannot be renamed
        mvc.perform(post("/api/v1/relations")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("partnerBpnl", partner, "partnerRole", "CUSTOMER", "policyProfile", "nope"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("policyProfile"));
        create(
                "/api/v1/relations",
                Map.of("partnerBpnl", partner, "partnerRole", "CUSTOMER", "policyProfile", code),
                TestJwts.manager());
        mvc.perform(put(PROFILES + "/" + id)
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile(code + "-renamed", "a:1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCED"));
        mvc.perform(put(PROFILES + "/" + id)
                        .with(TestJwts.admin())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile(code, "cx.quality.base:1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // referenced profile is deactivated instead of deleted
        mvc.perform(delete(PROFILES + "/" + id).with(TestJwts.admin())).andExpect(status().isNoContent());
        mvc.perform(get(PROFILES + "/" + id).with(TestJwts.admin()))
                .andExpect(jsonPath("$.active").value(false));

        String defaultId = defaultProfile.get("id").asString();
        mvc.perform(delete(PROFILES + "/" + defaultId).with(TestJwts.admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEFAULT_PROFILE_PROTECTED"));
        var deactivate = new java.util.LinkedHashMap<>(profile("default", "cx.quality.base:1"));
        deactivate.put("active", false);
        mvc.perform(put(PROFILES + "/" + defaultId)
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(deactivate)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/audit-events?objectType=POLICY_PROFILE&objectId=" + id)
                        .with(TestJwts.engineer()))
                .andExpect(status().isOk());
    }

    @Test
    void reconciliationAppliesTheProfileOfTheRelationToThePartnersOffers() throws Exception {
        String code = "r-" + partner.toLowerCase();
        when(connector.listContractDefinitionIds()).thenReturn(List.of());
        create(PROFILES, profile(code, "msg.ppap.productApproval:1"), TestJwts.admin());
        JsonNode relation = create(
                "/api/v1/relations", Map.of("partnerBpnl", partner, "partnerRole", "CUSTOMER"), TestJwts.manager());
        // changing the profile of the relation applies it to the EDC right away (result in the response)
        JsonNode updated =
                read(mvc.perform(put("/api/v1/relations/" + relation.get("id").asString())
                                .with(TestJwts.manager())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "partnerBpnl", partner, "partnerRole", "CUSTOMER", "policyProfile", code))))
                        .andExpect(status().isOk())
                        .andReturn());
        assertThat(updated.get("reconciliation").get("status").asString()).isEqualTo("APPLIED");
        assertThat(updated.get("reconciliation").get("contractDefinitionIds").toString())
                .contains("ppap-documents-" + partner + "-cd");
        assertThat(updated.get("reconciliation").get("policyIds").toString()).contains("ppap-usage-" + code + "-");
        JsonNode unchanged =
                read(mvc.perform(put("/api/v1/relations/" + relation.get("id").asString())
                                .with(TestJwts.manager())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of(
                                        "partnerBpnl",
                                        partner,
                                        "partnerRole",
                                        "CUSTOMER",
                                        "policyProfile",
                                        code,
                                        "active",
                                        true))))
                        .andExpect(status().isOk())
                        .andReturn());
        assertThat(unchanged.get("reconciliation").isNull()).isTrue();
        when(connector.listPolicies(anyInt()))
                .thenReturn(List.of(Map.of("@id", "ppap-usage-stale-00000000"), Map.of("@id", "foreign-policy")));
        clearInvocations(connector);
        ProvisioningState state = provisioning.reconcile();
        assertThat(state.ok()).as(state.error()).isTrue();
        String documents = "ppap-documents-" + partner;
        assertThat(state.usagePolicies().get(documents)).startsWith("ppap-usage-" + code + "-");
        assertThat(state.usagePolicies().get("ppap-notification-receive")).startsWith("ppap-usage-default-");

        var policies = ArgumentCaptor.forClass(PolicySpec.class);
        verify(connector, atLeastOnce()).ensurePolicy(policies.capture());
        PolicySpec usage = policies.getAllValues().stream()
                .filter(p -> p.id().equals(state.usagePolicies().get(documents)))
                .findFirst()
                .orElseThrow();
        assertThat(usage.constraints().get(1).rightOperand()).isEqualTo(List.of("msg.ppap.productApproval:1"));
        assertThat(policies.getAllValues()).anySatisfy(p -> {
            assertThat(p.id()).startsWith("ppap-access-bpn-" + partner + "-");
            assertThat(p.constraints().getFirst().leftOperand()).isEqualTo("Membership");
        });
        var definitions = ArgumentCaptor.forClass(ContractDefinitionSpec.class);
        verify(connector, atLeastOnce()).ensureContractDefinition(definitions.capture());
        assertThat(definitions.getAllValues()).anySatisfy(d -> {
            assertThat(d.assetId()).isEqualTo(documents);
            assertThat(d.contractPolicyId()).isEqualTo(usage.id());
        });
        verify(connector).removePolicy("ppap-usage-stale-00000000");
        verify(connector, org.mockito.Mockito.never()).removePolicy("foreign-policy");
    }

    @Test
    void cockpitReadsTheConnectorAndDataspaceIsVisibleForAllRoles() throws Exception {
        when(connector.listAssets(anyInt())).thenReturn(List.of(Map.of("@id", "ppap-public-keys", "@type", "Asset")));
        when(connector.listPolicies(anyInt())).thenReturn(List.of());
        when(connector.listContractDefinitions(anyInt())).thenReturn(List.of(Map.of("@id", "other-cd")));
        when(connector.listNegotiations(any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        Map.of("@id", "n1", "state", "FINALIZED", "counterPartyId", "did:web:wallet-stub:" + partner)));
        when(connector.listTransfers(any(), anyInt(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/v1/connector/overview").with(TestJwts.engineer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/connector/overview").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edc.status").value("UP"))
                .andExpect(jsonPath("$.counts.assets").value(1))
                .andExpect(jsonPath("$.counts.negotiations").value(1));
        mvc.perform(get("/api/v1/connector/assets").with(TestJwts.admin()))
                .andExpect(jsonPath("$[0].managed").value(true));
        mvc.perform(get("/api/v1/connector/contract-definitions").with(TestJwts.admin()))
                .andExpect(jsonPath("$[0].managed").value(false));
        mvc.perform(get("/api/v1/connector/policies").with(TestJwts.admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/connector/transfers?state=STARTED").with(TestJwts.admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/connector/negotiations?state=FINALIZED&page=0").with(TestJwts.admin()))
                .andExpect(jsonPath("$[0].partner.bpnl").value(partner))
                .andExpect(jsonPath("$[0].partner.name").value("Customer " + partner));
        mvc.perform(get("/api/v1/connector/negotiations?page=-1").with(TestJwts.admin()))
                .andExpect(status().isBadRequest());

        when(connector.requestFullCatalog(any()))
                .thenReturn(List.of(new CatalogDataset(
                        "ppap-notification-receive",
                        "https://w3id.org/catenax/taxonomy#PpapNotificationAPI",
                        Map.of("description", "x"),
                        List.of(Map.of("@id", "offer-1")))));
        mvc.perform(post("/api/v1/connector/catalog")
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerBpnl\":\"" + partner + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasets[0].assetId").value("ppap-notification-receive"))
                .andExpect(jsonPath("$.datasets[0].offers[0].@id").value("offer-1"));
        when(connector.requestFullCatalog(any()))
                .thenThrow(new DataspaceException(StepName.CATALOG, "Catalog request failed: HTTP 502"));
        mvc.perform(post("/api/v1/connector/catalog")
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerBpnl\":\"" + partner + "\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_REQUEST_FAILED"));
        mvc.perform(post("/api/v1/connector/catalog")
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        create(
                "/api/v1/relations",
                Map.of("partnerBpnl", partner, "partnerRole", "CUSTOMER", "exchangeMode", "PULL"),
                TestJwts.manager());
        JsonNode topology = read(mvc.perform(get("/api/v1/dataspace/topology").with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(topology.get("self").get("bpnl").asString()).isEqualTo("BPNL00000003AYRE");
        assertThat(topology.get("self").get("connector").get("status").asString())
                .isEqualTo("UP");
        JsonNode node = LiveJson.find(topology.get("partners"), "bpnl", partner);
        assertThat(node.get("exchangeReady").asBoolean()).isTrue();
        assertThat(node.get("relation").get("exchangeMode").asString()).isEqualTo("PULL");
        assertThat(node.get("relation").get("policyProfile").asString()).isEqualTo("default");
        assertThat(Set.copyOf(LiveJson.texts(node.get("roles")))).containsExactly("CUSTOMER");
        mvc.perform(get("/api/v1/dataspace/recent?limit=5").with(TestJwts.manager()))
                .andExpect(status().isOk());
    }

    static Map<String, Object> profile(String code, String purpose) {
        return Map.of(
                "code",
                code,
                "name",
                "Profile " + code,
                "access",
                Map.of("type", "BPN", "membershipRequired", true),
                "usage",
                Map.of(
                        "frameworkAgreement",
                        "DataExchangeGovernance:1.0",
                        "usagePurposes",
                        List.of(purpose),
                        "additionalConstraints",
                        List.of()));
    }

    JsonNode create(
            String path, Map<String, ?> body, org.springframework.test.web.servlet.request.RequestPostProcessor user)
            throws Exception {
        return read(mvc.perform(post(path)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode read(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    /** JSON array helpers. */
    static final class LiveJson {
        static JsonNode find(JsonNode array, String field, String value) {
            for (JsonNode node : array) {
                if (value.equals(node.get(field).asString())) {
                    return node;
                }
            }
            throw new AssertionError("no element with " + field + "=" + value + " in " + array);
        }

        static List<String> texts(JsonNode array) {
            return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                    .map(JsonNode::asString)
                    .toList();
        }
    }
}
