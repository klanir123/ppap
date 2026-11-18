package com.msg.ppap.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.audit.application.AuditService;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import com.msg.ppap.support.TestKeys;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class AuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    AuditService audit;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void masterdataChangesAreHashChainedPerObject() throws Exception {
        String number = TestKeys.partNumber();
        String body = mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("partNumber", number, "nameDe", "a"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = json.readTree(body).get("id").asString();
        mvc.perform(put("/api/v1/parts/" + id)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("partNumber", number, "nameDe", "b"))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/parts/" + id).with(TestJwts.manager())).andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> mvc.perform(get("/api/v1/audit-events")
                                .with(TestJwts.engineer())
                                .param("objectType", "PART")
                                .param("objectId", id))
                        .andExpect(jsonPath("$.totalElements").value(3)));

        mvc.perform(get("/api/v1/audit-events")
                        .with(TestJwts.engineer())
                        .param("objectType", "PART")
                        .param("objectId", id))
                .andExpect(jsonPath("$.content[0].action").value("DELETED"))
                .andExpect(jsonPath("$.content[1].action").value("UPDATED"))
                .andExpect(jsonPath("$.content[1].actor").value("client:ppap-integration"))
                .andExpect(jsonPath("$.content[2].action").value("CREATED"))
                .andExpect(jsonPath("$.content[2].actor").value("mia.manager"))
                .andExpect(jsonPath("$.content[2].prevHash").doesNotExist())
                .andExpect(jsonPath("$.content[2].details.businessKey").value(number))
                .andExpect(jsonPath("$.content[0].businessKey").value(number));

        assertThat(audit.verifyChain("PART", id)).isTrue();
        mvc.perform(get("/api/v1/audit-events").with(TestJwts.engineer()).param("size", "1"))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/audit-events")).andExpect(status().isUnauthorized());

        // append-only at database level
        assertThatThrownBy(() -> jdbc.update("update audit_event set actor = 'eve' where object_id = ?", id))
                .hasMessageContaining("append-only");
    }
}
