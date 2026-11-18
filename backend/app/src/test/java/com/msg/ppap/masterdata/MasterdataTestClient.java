package com.msg.ppap.masterdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.support.TestJwts;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Small helper to create master data through the REST API in tests. */
final class MasterdataTestClient {

    private final MockMvc mvc;
    private final JsonMapper json;

    MasterdataTestClient(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    JsonNode read(String body) {
        return json.readTree(body);
    }

    JsonNode create(String path, Map<String, ?> body) throws Exception {
        String response = mvc.perform(post(path)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return read(response);
    }

    JsonNode partner(String bpnl, String... roles) throws Exception {
        return create(
                "/api/v1/partners",
                Map.of(
                        "bpnl",
                        bpnl,
                        "name",
                        "Partner " + bpnl,
                        "roles",
                        List.of(roles),
                        "dspEndpoint",
                        "http://edc.example/api/v1/dsp",
                        "did",
                        "did:web:" + bpnl.toLowerCase()));
    }

    JsonNode part(String partNumber) throws Exception {
        return create("/api/v1/parts", Map.of("partNumber", partNumber, "nameDe", "Teil " + partNumber));
    }

    JsonNode relation(String partnerBpnl, String role) throws Exception {
        return create("/api/v1/relations", Map.of("partnerBpnl", partnerBpnl, "partnerRole", role));
    }

    JsonNode element(String code) throws Exception {
        return create(
                "/api/v1/catalog/elements",
                Map.of(
                        "code",
                        code,
                        "nameDe",
                        "Element " + code,
                        "nameEn",
                        "Element " + code,
                        "evidenceType",
                        "DOCUMENT"));
    }
}
