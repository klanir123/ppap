package com.msg.ppap.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import com.msg.ppap.support.TestKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MasterdataApiIntegrationTest extends AbstractIntegrationTest {

    static final String OWN_TENANT = "BPNL00000003AYRE";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper jsonMapper;

    MasterdataTestClient client;

    @BeforeEach
    void setUp() {
        client = new MasterdataTestClient(mvc, jsonMapper);
    }

    @Test
    void partCrudWithEtagAndIfMatch() throws Exception {
        String number = TestKeys.partNumber();
        var created = mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", number, "nameDe", "Halter", "material", "PA6"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWithId()))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String id = client.read(created.getResponse().getContentAsString())
                .get("id")
                .asString();

        mvc.perform(get("/api/v1/parts/" + id).with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.partNumber").value(number))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        var update = Map.of("partNumber", number, "nameDe", "Halter neu");
        mvc.perform(put("/api/v1/parts/" + id)
                        .with(TestJwts.manager())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(update)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.nameDe").value("Halter neu"))
                .andExpect(jsonPath("$.material").doesNotExist());

        // stale version
        mvc.perform(put("/api/v1/parts/" + id)
                        .with(TestJwts.manager())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(update)))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        // unchanged PUT keeps the version
        mvc.perform(put("/api/v1/parts/" + id)
                        .with(TestJwts.manager())
                        .header("If-Match", "W/\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(update)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));

        mvc.perform(delete("/api/v1/parts/" + id).with(TestJwts.manager()).header("If-Match", "\"7\""))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(delete("/api/v1/parts/" + id).with(TestJwts.manager()).header("If-Match", "\"1\""))
                .andExpect(status().isNoContent());
        // never referenced → physically deleted
        mvc.perform(get("/api/v1/parts/" + id).with(TestJwts.engineer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void upsertByPartNumberIsIdempotent() throws Exception {
        String number = TestKeys.partNumber() + "/01";
        java.net.URI path = java.net.URI.create("/api/v1/parts/by-number/" + number.replace("/", "%2F"));
        var body = client.toJson(Map.of("nameDe", "Deckel", "nameEn", "Cover"));
        mvc.perform(put(path)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partNumber").value(number))
                .andExpect(header().exists("Location"));
        mvc.perform(put(path)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(put(path)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", "OTHER", "nameDe", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("KEY_MISMATCH"));
    }

    @Test
    void validationAndDuplicateErrors() throws Exception {
        mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", "a#b"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath(
                        "$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("partNumber", "nameDe")));

        String number = TestKeys.partNumber();
        client.part(number);
        mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", number, "nameDe", "dup"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));

        mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void writeRequiresRole() throws Exception {
        var body = client.toJson(Map.of("partNumber", TestKeys.partNumber(), "nameDe", "x"));
        mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.engineer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/parts")
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parts")).andExpect(status().isUnauthorized());
        // connector admins may maintain partners
        mvc.perform(post("/api/v1/partners")
                        .with(TestJwts.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(
                                Map.of("bpnl", TestKeys.bpnl(), "name", "P", "roles", List.of("SUPPLIER")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exchangeReady").value(false));
    }

    @Test
    void paginationSearchAndSort() throws Exception {
        String prefix = "SRCH" + TestKeys.random(4);
        for (int i = 0; i < 5; i++) {
            client.part(prefix + "-" + i);
        }
        mvc.perform(get("/api/v1/parts")
                        .with(TestJwts.engineer())
                        .param("q", prefix.toLowerCase())
                        .param("size", "2")
                        .param("page", "1")
                        .param("sort", "partNumber,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content[0].partNumber").value(prefix + "-2"));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("sort", "hacker,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/parts")
                        .with(TestJwts.engineer())
                        .param("size", "1000")
                        .param("q", prefix))
                .andExpect(jsonPath("$.size").value(200));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("q", "100%_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void partnersSitesRelationsAndMappings() throws Exception {
        // own tenant cannot be a partner
        mvc.perform(post("/api/v1/partners")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("bpnl", OWN_TENANT, "name", "Me", "roles", List.of("CUSTOMER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REFERENCE"));

        String partnerBpnl = TestKeys.bpnl();
        JsonNode partner = client.partner(partnerBpnl, "CUSTOMER");
        assertThat(partner.get("exchangeReady").asBoolean()).isTrue();

        // sites: owner must be own tenant or a known partner
        mvc.perform(post("/api/v1/sites")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(
                                Map.of("bpns", TestKeys.bpns(), "name", "W", "ownerBpnl", TestKeys.bpnl()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_REFERENCE"));
        String ownSite = TestKeys.bpns();
        JsonNode site = client.create(
                "/api/v1/sites", Map.of("bpns", ownSite, "name", "Werk", "ownerBpnl", OWN_TENANT, "country", "de"));
        assertThat(site.get("ownerName").asString()).isEqualTo("Test OEM AG");
        assertThat(site.get("country").asString()).isEqualTo("DE");
        mvc.perform(put("/api/v1/sites/by-bpns/" + ownSite)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("name", "Werk 2", "ownerBpnl", partnerBpnl))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Partner " + partnerBpnl));
        mvc.perform(get("/api/v1/sites").with(TestJwts.engineer()).param("ownerBpnl", partnerBpnl))
                .andExpect(jsonPath("$.totalElements").value(1));

        // relation role must match partner roles
        mvc.perform(post("/api/v1/relations")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partnerBpnl", partnerBpnl, "partnerRole", "SUPPLIER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("ROLE_MISMATCH"));
        JsonNode relation = client.relation(partnerBpnl, "CUSTOMER");
        String relationId = relation.get("id").asString();
        assertThat(relation.get("exchangeMode").asString()).isEqualTo("PUSH_NOTIFY_PULL_PAYLOAD");
        assertThat(relation.get("partnerName").asString()).isEqualTo("Partner " + partnerBpnl);
        mvc.perform(post("/api/v1/relations")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partnerBpnl", partnerBpnl, "partnerRole", "CUSTOMER"))))
                .andExpect(status().isConflict());

        // part mappings
        String number = TestKeys.partNumber();
        JsonNode part = client.part(number);
        String mappingsPath = "/api/v1/relations/" + relationId + "/part-mappings";
        JsonNode mapping = client.create(
                mappingsPath, Map.of("partNumber", number, "partnerPartNumber", "K-" + number, "ownSiteBpns", ownSite));
        assertThat(mapping.get("partId").asString()).isEqualTo(part.get("id").asString());
        assertThat(mapping.get("partNameDe").asString()).isEqualTo("Teil " + number);
        mvc.perform(post(mappingsPath)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partId", part.get("id").asString(), "partnerPartNumber", "X"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"));
        String other = TestKeys.partNumber();
        client.part(other);
        mvc.perform(post(mappingsPath)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", other, "partnerPartNumber", "K-" + number))))
                .andExpect(status().isConflict());
        mvc.perform(get(mappingsPath).with(TestJwts.engineer()).param("q", number.toLowerCase()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/relations/" + relationId).with(TestJwts.engineer()))
                .andExpect(jsonPath("$.mappingCount").value(1));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("partnerBpnl", partnerBpnl))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partNumber").value(number));
        mvc.perform(get("/api/v1/relations")
                        .with(TestJwts.engineer())
                        .param("partnerBpnl", partnerBpnl)
                        .param("partnerRole", "CUSTOMER"))
                .andExpect(jsonPath("$.totalElements").value(1));

        // resolve (UC-04)
        mvc.perform(get("/api/v1/part-mappings/resolve")
                        .with(TestJwts.engineer())
                        .param("partnerBpnl", partnerBpnl)
                        .param("partnerRole", "CUSTOMER")
                        .param("partnerPartNumber", "K-" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partNumber").value(number));
        mvc.perform(get("/api/v1/part-mappings/resolve")
                        .with(TestJwts.engineer())
                        .param("partnerBpnl", partnerBpnl)
                        .param("partnerRole", "CUSTOMER")
                        .param("partnerPartNumber", "UNKNOWN"))
                .andExpect(status().isNotFound());

        // referenced objects are deactivated, not deleted; key changes are rejected
        mvc.perform(put("/api/v1/partners/" + partner.get("id").asString())
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(
                                Map.of("bpnl", TestKeys.bpnl(), "name", "x", "roles", List.of("CUSTOMER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCED"));
        mvc.perform(delete("/api/v1/parts/" + part.get("id").asString()).with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/parts/" + part.get("id").asString()).with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/parts")
                        .with(TestJwts.engineer())
                        .param("q", number)
                        .param("active", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(delete("/api/v1/relations/" + relationId).with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/relations/" + relationId).with(TestJwts.engineer()))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete("/api/v1/sites/" + site.get("id").asString()).with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sites/" + site.get("id").asString()).with(TestJwts.engineer()))
                .andExpect(jsonPath("$.active").value(false));

        // mappings are deleted physically; afterwards the relation is no longer referenced
        String mappingPath = mappingsPath + "/" + mapping.get("id").asString();
        mvc.perform(put(mappingPath)
                        .with(TestJwts.manager())
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("partNumber", number, "partnerPartNumber", "K2-" + number))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete(mappingPath).with(TestJwts.manager())).andExpect(status().isNoContent());
        mvc.perform(get(mappingPath).with(TestJwts.engineer())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/relations/" + relationId).with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/relations/" + relationId).with(TestJwts.engineer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/relations/" + relationId + "/part-mappings").with(TestJwts.engineer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void partnerUpsertAndListFilters() throws Exception {
        String bpnl = TestKeys.bpnl();
        var body = new HashMap<String, Object>(Map.of("name", "Zulieferer", "roles", List.of("SUPPLIER", "CUSTOMER")));
        mvc.perform(put("/api/v1/partners/by-bpnl/" + bpnl)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.roles[1]").value("SUPPLIER"));
        body.put("active", false);
        mvc.perform(put("/api/v1/partners/by-bpnl/" + bpnl)
                        .with(TestJwts.integration())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/partners")
                        .with(TestJwts.engineer())
                        .param("active", "false")
                        .param("size", "200"))
                .andExpect(jsonPath("$.content[*].bpnl", hasItem(bpnl)));
        mvc.perform(get("/api/v1/partners")
                        .with(TestJwts.engineer())
                        .param("active", "true")
                        .param("size", "200"))
                .andExpect(jsonPath("$.content[*].bpnl", not(hasItem(bpnl))));
        mvc.perform(get("/api/v1/sites").with(TestJwts.engineer()).param("ownerBpnl", "nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void catalogElementsLevelsReasonsAndTransfer() throws Exception {
        String code = TestKeys.code();
        mvc.perform(post("/api/v1/catalog/elements")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "code", code,
                                "nameDe", "Werte",
                                "nameEn", "Values",
                                "evidenceType", "VALUES",
                                "valueSchema", Map.of("type", 42)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("valueSchema"));
        JsonNode element = client.create(
                "/api/v1/catalog/elements",
                Map.of(
                        "code",
                        code,
                        "nameDe",
                        "Werte",
                        "nameEn",
                        "Values",
                        "evidenceType",
                        "VALUES",
                        "sortOrder",
                        5,
                        "valueSchema",
                        Map.of("type", "array", "items", Map.of("type", "number"))));
        assertThat(element.get("valueSchema").get("type").asString()).isEqualTo("array");

        String levelCode = TestKeys.code();
        mvc.perform(post("/api/v1/catalog/levels")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "code",
                                levelCode,
                                "nameDe",
                                "S",
                                "nameEn",
                                "L",
                                "requirements",
                                Map.of("NOPE-1", "REQUIRED")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_CODE"))
                .andExpect(jsonPath("$.errors[0].field").value("requirements.NOPE-1"));
        JsonNode level = client.create(
                "/api/v1/catalog/levels",
                Map.of(
                        "code", levelCode,
                        "nameDe", "Stufe",
                        "nameEn", "Level",
                        "scheme", "VDA",
                        "requirements", Map.of(code, "REQUIRED")));
        assertThat(level.get("requirements").get(code).asString()).isEqualTo("REQUIRED");

        mvc.perform(put("/api/v1/catalog/levels/" + level.get("id").asString())
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "code", levelCode, "nameDe", "Stufe", "nameEn", "Level", "requirements", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements").isEmpty());
        mvc.perform(put("/api/v1/catalog/levels/by-code/" + levelCode)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "nameDe", "Stufe", "nameEn", "Level", "requirements", Map.of(code, "ON_REQUEST")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirements." + code).value("ON_REQUEST"));

        // element referenced by a level: code change rejected, delete deactivates
        mvc.perform(put("/api/v1/catalog/elements/" + element.get("id").asString())
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "code", TestKeys.code(), "nameDe", "a", "nameEn", "b", "evidenceType", "DOCUMENT"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCED"));
        mvc.perform(delete("/api/v1/catalog/elements/" + element.get("id").asString())
                        .with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/elements/" + element.get("id").asString())
                        .with(TestJwts.engineer()))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/catalog/elements").with(TestJwts.engineer()).param("active", "true"))
                .andExpect(jsonPath("$[*].code", not(hasItem(code))));

        String reasonCode = TestKeys.code();
        mvc.perform(put("/api/v1/catalog/reasons/by-code/" + reasonCode)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of("nameDe", "Neuteil", "nameEn", "New part", "sortOrder", 1))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/catalog/reasons").with(TestJwts.engineer()))
                .andExpect(jsonPath("$[*].code", hasItem(reasonCode)));

        // export → import round trip is unchanged
        String export = mvc.perform(get("/api/v1/catalog/export").with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[*].code", hasItem(code)))
                .andExpect(jsonPath("$.levels[*].code", hasItem(levelCode)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        mvc.perform(post("/api/v1/catalog/import")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(export))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("catalog"))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        // invalid import: nothing is imported
        String newCode = TestKeys.code();
        mvc.perform(post("/api/v1/catalog/import")
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(client.toJson(Map.of(
                                "elements",
                                List.of(
                                        Map.of(
                                                "code",
                                                newCode,
                                                "nameDe",
                                                "a",
                                                "nameEn",
                                                "b",
                                                "evidenceType",
                                                "DOCUMENT"),
                                        Map.of("code", "x", "nameDe", "a", "nameEn", "b", "evidenceType", "DOCUMENT")),
                                "levels",
                                List.of(),
                                "reasons",
                                List.of()))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.failures[0].rowNumber").value(2))
                .andExpect(jsonPath("$.failures[0].messages[0].field").value("elements[1].code"));
        mvc.perform(get("/api/v1/catalog/elements").with(TestJwts.engineer()))
                .andExpect(jsonPath("$[*].code", not(hasItem(newCode))));

        // levels and reasons are deleted physically while unreferenced
        mvc.perform(delete("/api/v1/catalog/levels/" + level.get("id").asString())
                        .with(TestJwts.manager()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/catalog/levels/" + level.get("id").asString()).with(TestJwts.engineer()))
                .andExpect(status().isNotFound());
    }

    private static org.hamcrest.Matcher<String> endsWithId() {
        return org.hamcrest.Matchers.matchesPattern(".*/api/v1/parts/[0-9a-f-]{36}$");
    }
}
