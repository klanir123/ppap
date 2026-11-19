package com.msg.ppap.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.masterdata.adapter.in.seed.SeedLoader;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SeedLoaderIntegrationTest extends AbstractIntegrationTest {

    static final Path FIXTURE = Path.of("src/test/resources/seed-fixture");

    @Autowired
    SeedLoader seedLoader;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void loadsSeedThroughImportServicesIdempotently() throws Exception {
        Map<String, Object> first =
                seedLoader.load(FIXTURE.resolve("common"), FIXTURE.resolve("tier1"), FIXTURE.resolve("documents"));
        assertThat(first.get("approval-cases.json"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("failed", 0);
        Map<String, Object> second =
                seedLoader.load(FIXTURE.resolve("common"), FIXTURE.resolve("tier1"), FIXTURE.resolve("documents"));

        assertThat(second)
                .containsKeys(
                        "catalog.json",
                        "partners.csv",
                        "part-mappings.csv",
                        "form-templates.json",
                        "approval-cases.json");
        assertThat(second.values())
                .allSatisfy(result -> assertThat(result)
                        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                        .containsEntry("created", 0)
                        .containsEntry("updated", 0)
                        .containsEntry("failed", 0));
        assertThat(seedLoader.load(null, FIXTURE.resolve("does-not-exist")))
                .containsEntry("parts.csv", "missing")
                .containsEntry("approval-cases.json", "missing");

        mvc.perform(get("/api/v1/part-mappings/resolve")
                        .with(TestJwts.engineer())
                        .param("partnerBpnl", "BPNLFIXTURE00001")
                        .param("partnerRole", "CUSTOMER")
                        .param("partnerPartNumber", "OEM-A-4711"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value("FX-4711"))
                .andExpect(jsonPath("$.partnerSiteBpns").value("BPNSFIXTURE00002"));
        mvc.perform(get("/api/v1/catalog/levels").with(TestJwts.engineer()))
                .andExpect(
                        jsonPath("$[?(@.code == 'FX-L2')].requirements.FX-02").value("ON_REQUEST"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                            "select count(*) from audit_event where actor = 'system:seed' and object_type = 'SEED'",
                            Integer.class))
                    .isGreaterThanOrEqualTo(3);
            assertThat(jdbc.queryForObject(
                            "select count(*) from md_part where part_number = 'FX-4711' and created_by = 'system:seed'",
                            Integer.class))
                    .isEqualTo(1);
        });
    }
}
