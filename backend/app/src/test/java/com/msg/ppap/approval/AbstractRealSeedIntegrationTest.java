package com.msg.ppap.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.masterdata.adapter.in.seed.SeedLoader;
import com.msg.ppap.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Loads the real seed data of the repository ({@code seed/}) of one participant with its tenant BPNL, if present;
 * skipped otherwise. Ensures the generated seed files load without failures (subclasses per participant).
 */
abstract class AbstractRealSeedIntegrationTest extends AbstractIntegrationTest {

    static final Path SEED = Path.of("../../seed");

    @Autowired
    SeedLoader seedLoader;

    @Autowired
    JdbcTemplate jdbc;

    abstract String participant();

    abstract String tenant();

    abstract int minimumCases();

    @Test
    void realSeedLoadsWithoutFailures() {
        Path folder = SEED.resolve(participant());
        Assumptions.assumeTrue(Files.isRegularFile(folder.resolve("approval-cases.json")), "no seed folder");
        Map<String, Object> summary = seedLoader.load(SEED.resolve("common"), folder, SEED.resolve("documents"));
        summary.forEach((file, result) -> assertThat(result)
                .as(file)
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("failed", 0));
        assertThat(summary.get("approval-cases.json"))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("unchanged", 0);
        Map<String, Object> again = seedLoader.load(SEED.resolve("common"), folder, SEED.resolve("documents"));
        assertThat(again.get("approval-cases.json"))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("created", 0)
                .containsEntry("failed", 0);
        assertThat(jdbc.queryForObject("select count(*) from ap_case where tenant_id = ?", Integer.class, tenant()))
                .isGreaterThanOrEqualTo(minimumCases());
    }
}
