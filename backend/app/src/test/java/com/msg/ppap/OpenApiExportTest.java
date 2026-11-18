package com.msg.ppap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exports the OpenAPI document to {@code api/openapi.json} (repository root). The file is the contract for the
 * frontend's generated client (orval) and is committed; review diffs to it like code.
 */
class OpenApiExportTest extends AbstractIntegrationTest {

    /** Relative to the module base directory (Surefire's working directory). */
    static final Path TARGET = Path.of("../../api/openapi.json");

    @Autowired
    MockMvc mvc;

    @Test
    void exportsOpenApiDocument() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(json)
                .contains("\"title\" : \"msg CX PPAP Connector API\"")
                .contains("\"ConfigResponse\"")
                .contains("\"MeResponse\"")
                .contains("\"SystemStatusResponse\"")
                .contains("\"bearerAuth\"");

        Files.createDirectories(TARGET.toAbsolutePath().getParent());
        Files.writeString(TARGET, json.endsWith("\n") ? json : json + "\n", StandardCharsets.UTF_8);
    }
}
