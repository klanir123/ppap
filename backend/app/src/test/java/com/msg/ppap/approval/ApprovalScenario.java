package com.msg.ppap.approval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.support.TestJwts;
import com.msg.ppap.support.TestKeys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Master data of an approval test (random keys; tests share one database): a supplier partner with a mapped customer
 * part, a customer partner with a mapped supplier part, two catalogue elements (document, values with schema), a
 * level requiring both and a reason.
 */
final class ApprovalScenario {

    static final String TENANT = "BPNL00000003AYRE";
    static final Map<String, Object> VALUE_SCHEMA = Map.of(
            "$schema",
            "https://json-schema.org/draft/2020-12/schema",
            "type",
            "object",
            "properties",
            Map.of("quantity", Map.of("type", "integer", "minimum", 1)),
            "required",
            List.of("quantity"));

    final MockMvc mvc;
    final JsonMapper json;

    final String supplierBpnl = TestKeys.bpnl();
    final String customerBpnl = TestKeys.bpnl();
    final String docElement = TestKeys.code();
    final String valueElement = TestKeys.code();
    final String optionalElement = TestKeys.code();
    final String level = TestKeys.code();
    final String reason = TestKeys.code();
    final String customerPartNumber = TestKeys.partNumber();
    final String supplierPartNumberAtCustomer = TestKeys.partNumber();
    final String supplierPartNumber = TestKeys.partNumber();
    final String customerNumberAtSupplier = TestKeys.partNumber();
    UUID customerPartId;
    UUID supplierPartId;
    UUID supplierRelationId;
    UUID customerRelationId;

    ApprovalScenario(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    static JwtRequestPostProcessor engineer() {
        return TestJwts.user("oem.quality", "CUSTOMER_QUALITY_ENGINEER");
    }

    static JwtRequestPostProcessor supplier() {
        return TestJwts.user("tier1.quality", "SUPPLIER_QUALITY_MANAGER");
    }

    ApprovalScenario create() throws Exception {
        create(
                "/api/v1/partners",
                Map.of(
                        "bpnl",
                        supplierBpnl,
                        "name",
                        "Supplier " + supplierBpnl,
                        "roles",
                        List.of("SUPPLIER"),
                        "dspEndpoint",
                        "http://edc.example/api/v1/dsp",
                        "did",
                        "did:web:" + supplierBpnl.toLowerCase()));
        create(
                "/api/v1/partners",
                Map.of("bpnl", customerBpnl, "name", "Customer " + customerBpnl, "roles", List.of("CUSTOMER")));
        customerPartId =
                UUID.fromString(create("/api/v1/parts", Map.of("partNumber", customerPartNumber, "nameDe", "Türgriff"))
                        .get("id")
                        .asString());
        supplierPartId =
                UUID.fromString(create("/api/v1/parts", Map.of("partNumber", supplierPartNumber, "nameDe", "Rastnase"))
                        .get("id")
                        .asString());
        supplierRelationId = UUID.fromString(
                create("/api/v1/relations", Map.of("partnerBpnl", supplierBpnl, "partnerRole", "SUPPLIER"))
                        .get("id")
                        .asString());
        customerRelationId = UUID.fromString(
                create("/api/v1/relations", Map.of("partnerBpnl", customerBpnl, "partnerRole", "CUSTOMER"))
                        .get("id")
                        .asString());
        create(
                "/api/v1/relations/" + supplierRelationId + "/part-mappings",
                Map.of("partId", customerPartId.toString(), "partnerPartNumber", supplierPartNumberAtCustomer));
        create(
                "/api/v1/relations/" + customerRelationId + "/part-mappings",
                Map.of("partId", supplierPartId.toString(), "partnerPartNumber", customerNumberAtSupplier));
        create(
                "/api/v1/catalog/elements",
                Map.of(
                        "code",
                        docElement,
                        "nameDe",
                        "Deckblatt",
                        "nameEn",
                        "Cover sheet",
                        "evidenceType",
                        "DOCUMENT",
                        "sortOrder",
                        1));
        create(
                "/api/v1/catalog/elements",
                Map.of(
                        "code",
                        valueElement,
                        "nameDe",
                        "Erstmuster",
                        "nameEn",
                        "Initial samples",
                        "evidenceType",
                        "VALUES",
                        "valueSchema",
                        VALUE_SCHEMA,
                        "sortOrder",
                        2));
        create(
                "/api/v1/catalog/elements",
                Map.of(
                        "code",
                        optionalElement,
                        "nameDe",
                        "Optional",
                        "nameEn",
                        "Optional",
                        "evidenceType",
                        "DOCUMENT",
                        "sortOrder",
                        3));
        create(
                "/api/v1/catalog/levels",
                Map.of(
                        "code",
                        level,
                        "nameDe",
                        "Stufe",
                        "nameEn",
                        "Level",
                        "scheme",
                        "VDA",
                        "requirements",
                        Map.of(docElement, "REQUIRED", valueElement, "REQUIRED", optionalElement, "ON_REQUEST")));
        create("/api/v1/catalog/reasons", Map.of("code", reason, "nameDe", "Neuteil", "nameEn", "New part"));
        return this;
    }

    JsonNode create(String path, Map<String, ?> body) throws Exception {
        return read(mvc.perform(post(path)
                        .with(TestJwts.manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    JsonNode read(String body) {
        return json.readTree(body);
    }

    String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    /** Uploads a small PDF and returns its document id. */
    String uploadPdf(String name) throws Exception {
        var file = new MockMultipartFile("file", name, "application/pdf", pdf(name));
        return read(mvc.perform(multipart("/api/v1/documents").file(file).with(supplier()))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8))
                .get("id")
                .asString();
    }

    static byte[] pdf(String text) {
        return ("%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n% " + text + "\ntrailer << /Root 1 0 R >>\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII);
    }
}
