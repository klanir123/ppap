package com.msg.ppap.live;

import static com.msg.ppap.live.LiveClient.app;
import static com.msg.ppap.live.LiveClient.stream;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Phase 6 end-to-end (UC-10, AK-10-3): a policy profile assigned to the relation with the OEM at Tier-1 changes the
 * usage policy that Tier-1's EDC actually offers to the OEM – verified from the partner side with the live catalog of
 * the OEM's connector cockpit ({@code POST /connector/catalog}); the shared assets keep the profile {@code default}.
 * Afterwards the relation is switched back. Uses the demo users {@code tier1.admin} / {@code oem.admin}
 * (CONNECTOR_ADMIN).
 *
 * <p>Run with {@code ./mvnw -pl backend/app -am -Plive verify -Dtest=PolicyProfileLiveTest}.
 */
@Tag("live")
class PolicyProfileLiveTest {

    static final String OEM_BPNL = System.getProperty("live.oemBpnl", "BPNL00000000OEM1");
    static final String TIER1_BPNL = System.getProperty("live.tier1Bpnl", "BPNL000000TIER01");
    static final String PURPOSE = "msg.ppap.productApproval:1";
    static final Duration TIMEOUT = Duration.ofSeconds(60);

    final LiveClient c = new LiveClient();

    @Test
    void profileChangeIsVisibleInThePartnersCatalog() throws Exception {
        String tier1 = c.userToken("tier1", "tier1.admin");
        String oem = c.userToken("oem", "oem.admin");
        String code = "live-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode profile = c.send(
                "POST",
                app("tier1") + "/api/v1/policy-profiles",
                tier1,
                Map.of(
                        "code",
                        code,
                        "name",
                        "Live individual purpose",
                        "access",
                        Map.of("type", "BPN", "membershipRequired", true),
                        "usage",
                        Map.of(
                                "frameworkAgreement",
                                "DataExchangeGovernance:1.0",
                                "usagePurposes",
                                List.of(PURPOSE),
                                "additionalConstraints",
                                List.of())));
        String profileId = profile.path("id").asString();
        JsonNode preview = c.get(app("tier1") + "/api/v1/policy-profiles/" + profileId + "/preview", tier1);
        assertThat(preview.toString()).contains(PURPOSE).contains("Membership");

        JsonNode relation = relationToOem(tier1);
        try {
            long t = System.nanoTime();
            assign(tier1, relation, code);
            JsonNode offered = c.await("profile visible in OEM's live catalog of Tier-1", t, TIMEOUT, () -> {
                JsonNode catalog = catalog(oem);
                return purposes(catalog, "ppap-documents-" + OEM_BPNL).contains(PURPOSE) ? catalog : null;
            });
            assertThat(purposes(offered, "ppap-submission-" + OEM_BPNL)).contains(PURPOSE);
            assertThat(purposes(offered, "ppap-notification-receive"))
                    .doesNotContain(PURPOSE)
                    .contains("cx.quality.base:1");
            JsonNode overview = c.get(app("tier1") + "/api/v1/connector/overview", tier1);
            assertThat(overview.path("provisioning")
                            .path("usagePolicies")
                            .path("ppap-documents-" + OEM_BPNL)
                            .asString())
                    .startsWith("ppap-usage-" + code + "-");
        } finally {
            long t = System.nanoTime();
            assign(tier1, relationToOem(tier1), "default");
            c.await("default profile back in OEM's catalog", t, TIMEOUT, () -> {
                JsonNode catalog = catalog(oem);
                return purposes(catalog, "ppap-documents-" + OEM_BPNL).contains(PURPOSE) ? null : catalog;
            });
            JsonNode current = c.get(app("tier1") + "/api/v1/policy-profiles/" + profileId, tier1);
            c.send(
                    "DELETE",
                    app("tier1") + "/api/v1/policy-profiles/" + profileId,
                    tier1,
                    null,
                    Map.of("If-Match", "\"" + current.path("version").asLong() + "\""));
        }
        System.out.println("POLICY PROFILE LIVE TIMINGS (ms): " + c.timings);
    }

    private JsonNode relationToOem(String token) throws Exception {
        return stream(c.get(app("tier1") + "/api/v1/relations?partnerBpnl=" + OEM_BPNL + "&partnerRole=CUSTOMER", token)
                        .path("content"))
                .findFirst()
                .orElseThrow();
    }

    private void assign(String token, JsonNode relation, String profileCode) throws Exception {
        c.send(
                "PUT",
                app("tier1") + "/api/v1/relations/" + relation.path("id").asString(),
                token,
                Map.of(
                        "partnerBpnl", relation.path("partnerBpnl").asString(),
                        "partnerRole", relation.path("partnerRole").asString(),
                        "exchangeMode", relation.path("exchangeMode").asString(),
                        "policyProfile", profileCode,
                        "active", relation.path("active").asBoolean()),
                Map.of("If-Match", "\"" + relation.path("version").asLong() + "\""));
    }

    private JsonNode catalog(String oemToken) throws Exception {
        return c.send("POST", app("oem") + "/api/v1/connector/catalog", oemToken, Map.of("partnerBpnl", TIER1_BPNL));
    }

    /** UsagePurpose values of the offers of an asset in the catalog. */
    private static String purposes(JsonNode catalog, String assetId) {
        return stream(catalog.path("datasets"))
                .filter(d -> assetId.equals(d.path("assetId").asString()))
                .findFirst()
                .map(d -> d.path("offers").toString())
                .orElse("");
    }
}
