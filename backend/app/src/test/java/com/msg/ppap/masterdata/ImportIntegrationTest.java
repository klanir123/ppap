package com.msg.ppap.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import com.msg.ppap.support.TestKeys;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class ImportIntegrationTest extends AbstractIntegrationTest {

    static final String BOM = "﻿";

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
    void previewSemicolonCsvWithBomAndSynonymsNeverWrites() throws Exception {
        String a = TestKeys.partNumber();
        String b = TestKeys.partNumber();
        String csv = BOM + "Sachnummer;Bezeichnung;Werkstoff;Unbekannt\n"
                + a + ";Halter;PA6;x\n"
                + b + ";;;y\n"
                + a + ";Doppelt;;z\n";
        mvc.perform(upload("/api/v1/imports/parts/preview", csv("teile.csv", csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity").value("parts"))
                .andExpect(jsonPath("$.fileName").value("teile.csv"))
                .andExpect(jsonPath("$.delimiter").value(";"))
                .andExpect(jsonPath("$.columns[0]").value("Sachnummer"))
                .andExpect(jsonPath("$.suggestedMapping.Sachnummer").value("partNumber"))
                .andExpect(jsonPath("$.suggestedMapping.Bezeichnung").value("nameDe"))
                .andExpect(jsonPath("$.suggestedMapping.Werkstoff").value("material"))
                .andExpect(jsonPath("$.suggestedMapping.Unbekannt").isEmpty())
                .andExpect(jsonPath("$.targetFields[0].name").value("partNumber"))
                .andExpect(jsonPath("$.targetFields[0].required").value(true))
                .andExpect(jsonPath("$.targetFields[0].labelKey").value("masterdata.parts.partNumber"))
                .andExpect(jsonPath("$.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.rows[0].status").value("OK"))
                .andExpect(jsonPath("$.rows[0].action").value("CREATE"))
                .andExpect(jsonPath("$.rows[0].values.material").value("PA6"))
                .andExpect(jsonPath("$.rows[1].status").value("ERROR"))
                .andExpect(jsonPath("$.rows[1].messages[0].field").value("nameDe"))
                .andExpect(jsonPath("$.rows[1].messages[0].code").value("REQUIRED"))
                .andExpect(jsonPath("$.rows[2].messages[0].code").value("DUPLICATE_KEY"))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.ok").value(1))
                .andExpect(jsonPath("$.summary.errors").value(2))
                .andExpect(jsonPath("$.summary.create").value(1));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("q", a))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void executeIsAllOrNothingUnlessSkipErrorsAndIdempotent() throws Exception {
        String a = TestKeys.partNumber();
        String b = TestKeys.partNumber();
        String csv = "partNumber,nameDe,nameEn\n" + a + ",\"Halter, links\",Bracket\n" + b + ",,\n";

        mvc.perform(upload("/api/v1/imports/parts/execute", csv("parts.csv", csv)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.failures[0].rowNumber").value(3));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("q", a))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(upload("/api/v1/imports/parts/execute", csv("parts.csv", csv))
                        .param("skipErrors", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(1));
        mvc.perform(get("/api/v1/parts").with(TestJwts.engineer()).param("q", a))
                .andExpect(jsonPath("$.content[0].nameDe").value("Halter, links"));

        String fixed = "partNumber,nameDe,nameEn\n" + a + ",\"Halter, links\",Bracket\n" + b + ",Deckel,\n";
        mvc.perform(upload("/api/v1/imports/parts/execute", csv("parts.csv", fixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.unchanged").value(1));
        mvc.perform(upload("/api/v1/imports/parts/execute", csv("parts.csv", fixed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.unchanged").value(2));
    }

    @Test
    void xlsxImportUsesEntitySheetAndExplicitMapping() throws Exception {
        String bpnl = TestKeys.bpnl();
        byte[] xlsx;
        try (var workbook = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            workbook.createSheet("Info").createRow(0).createCell(0).setCellValue("ignore me");
            var sheet = workbook.createSheet("Partners");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("BPN");
            header.createCell(1).setCellValue("Firma");
            header.createCell(2).setCellValue("Rolle");
            header.createCell(3).setCellValue("Kontakt");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(bpnl);
            row.createCell(1).setCellValue("Excel GmbH");
            row.createCell(2).setCellValue("CUSTOMER|SUPPLIER");
            row.createCell(3).setCellValue(4711); // numeric cell, deliberately mapped to nothing
            workbook.write(out);
            xlsx = out.toByteArray();
        }
        var file = new MockMultipartFile(
                "file", "partner.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mvc.perform(upload("/api/v1/imports/partners/preview", file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheet").value("Partners"))
                .andExpect(jsonPath("$.suggestedMapping.BPN").value("bpnl"))
                .andExpect(jsonPath("$.suggestedMapping.Kontakt").value("contactEmail"))
                .andExpect(jsonPath("$.rows[0].status").value("ERROR"))
                .andExpect(jsonPath("$.rows[0].values.contactEmail").value("4711"));

        var mapping = new MockMultipartFile(
                "mapping",
                "",
                "application/json",
                client.toJson(Map.of("BPN", "bpnl", "Firma", "name", "Rolle", "roles"))
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(upload("/api/v1/imports/partners/preview", file).file(mapping))
                .andExpect(jsonPath("$.rows[0].status").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].messages[0].code").value("NOT_EXCHANGE_READY"));
        mvc.perform(upload("/api/v1/imports/partners/execute", file).file(mapping))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(get("/api/v1/partners").with(TestJwts.engineer()).param("q", bpnl))
                .andExpect(jsonPath("$.content[0].roles.length()").value(2));

        var badMapping = new MockMultipartFile(
                "mapping", "", "application/json", "{\"BPN\":\"nope\"}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(upload("/api/v1/imports/partners/preview", file).file(badMapping))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mapping.BPN"));
    }

    @Test
    void dependentEntitiesAndErrorReport() throws Exception {
        String partner = TestKeys.bpnl();
        String part = TestKeys.partNumber();
        String site = TestKeys.bpns();
        execute(
                "partners",
                "bpnl;name;roles;dspEndpoint;did\n" + partner + ";Kunde AG;CUSTOMER;http://edc;did:web:k\n");
        execute("sites", "bpns;name;ownerBpnl;country\n" + site + ";Werk;" + partner + ";DE\n");
        execute("parts", "partNumber;nameDe\n" + part + ";Teil\n");
        execute("relations", "partnerBpnl;partnerRole;exchangeMode\n" + partner + ";CUSTOMER;PUSH\n");
        String mappings = "partnerBpnl;partnerRole;partNumber;partnerPartNumber;partnerSiteBpns\n"
                + partner + ";CUSTOMER;" + part + ";KD-" + part + ";" + site + "\n"
                + partner + ";SUPPLIER;" + part + ";KD-2;\n"
                + partner + ";CUSTOMER;UNKNOWN-PART;KD-3;\n";
        mvc.perform(upload("/api/v1/imports/part-mappings/preview", csv("m.csv", mappings)))
                .andExpect(jsonPath("$.rows[0].status").value("OK"))
                .andExpect(jsonPath("$.rows[1].messages[0].code").value("UNKNOWN_REFERENCE"))
                .andExpect(jsonPath("$.rows[2].messages[0].field").value("partNumber"));

        String report = mvc.perform(upload("/api/v1/imports/part-mappings/error-report", csv("m.csv", mappings)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(report).startsWith(BOM + "rowNumber;partnerBpnl;");
        assertThat(report.lines()).hasSize(3);
        assertThat(report).contains("UNKNOWN_REFERENCE");

        mvc.perform(upload("/api/v1/imports/part-mappings/execute", csv("m.csv", mappings))
                        .param("skipErrors", "true"))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(2));
        mvc.perform(get("/api/v1/relations").with(TestJwts.engineer()).param("partnerBpnl", partner))
                .andExpect(jsonPath("$.content[0].mappingCount").value(1))
                .andExpect(jsonPath("$.content[0].exchangeMode").value("PUSH"));
        mvc.perform(get("/api/v1/sites").with(TestJwts.engineer()).param("q", site))
                .andExpect(jsonPath("$.content[*].ownerName", hasItem("Kunde AG")));
    }

    @Test
    void fileProblems() throws Exception {
        mvc.perform(upload("/api/v1/imports/parts/preview", csv("x.pdf", "abc")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(upload("/api/v1/imports/parts/preview", csv("x.csv", "\n\n")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        mvc.perform(upload(
                        "/api/v1/imports/parts/preview",
                        new MockMultipartFile("file", "x.csv", "text/csv", new byte[] {(byte) 0xC3, (byte) 0x28})))
                .andExpect(jsonPath("$.code").value("UNREADABLE_FILE"));
        mvc.perform(upload(
                        "/api/v1/imports/parts/preview",
                        new MockMultipartFile(
                                "file",
                                "x.xlsx",
                                "application/octet-stream",
                                "not a zip".getBytes(StandardCharsets.UTF_8))))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        mvc.perform(upload("/api/v1/imports/unknown/preview", csv("x.csv", "a\n1\n")))
                .andExpect(status().isNotFound());
        var rows = new StringBuilder("partNumber;nameDe\n");
        for (int i = 0; i <= 10_000; i++) {
            rows.append("P").append(i).append(";x\n");
        }
        mvc.perform(upload("/api/v1/imports/parts/preview", csv("big.csv", rows.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ROWS"));
        mvc.perform(upload(
                        "/api/v1/imports/parts/preview",
                        new MockMultipartFile("file", "huge.csv", "text/csv", new byte[10 * 1024 * 1024 + 1])))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/imports/parts/preview")
                        .file(csv("x.csv", "a\n1\n"))
                        .with(TestJwts.engineer()))
                .andExpect(status().isForbidden());
    }

    private void execute(String entity, String csv) throws Exception {
        mvc.perform(upload("/api/v1/imports/" + entity + "/execute", csv(entity + ".csv", csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(0));
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartHttpServletRequestBuilder upload(String path, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder builder = multipart(path);
        builder.file(file);
        builder.with(TestJwts.manager());
        return builder;
    }
}
