package com.msg.ppap.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.masterdata.adapter.in.seed.SeedLoader;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seed import of form templates and approval cases, and the Excel form import against the seeded template (the
 * fixture catalogue has no {@code PPF-01}, so the original file is attached to {@code FX-01}).
 */
@TestPropertySource(properties = "ppap.approval.form-import.attach-to-element=FX-01")
class ApprovalSeedAndExcelIntegrationTest extends AbstractIntegrationTest {

    static final Path FIXTURE = Path.of("src/test/resources/seed-fixture");
    static final String CASES = "/api/v1/approval-cases";
    static final String SUPPLIER_CASE = "11111111-2222-5333-8444-555555555501";

    @Autowired
    SeedLoader seedLoader;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        seedLoader.load(FIXTURE.resolve("common"), FIXTURE.resolve("tier1"), FIXTURE.resolve("documents"));
    }

    @Test
    void seededCasesTemplatesAndCaseNumbers() throws Exception {
        mvc.perform(get("/api/v1/form-templates").with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PPF-FORM-V1"))
                .andExpect(jsonPath("$[0].marker.cell").value("A1"))
                .andExpect(jsonPath("$[0].elementTable.columns.selfAssessment").value("D"))
                .andExpect(jsonPath("$[0].selfAssessmentValues['i.O.']").value("OK"));

        String legacyId = id("11111111-2222-5333-8444-555555555502");
        mvc.perform(get(CASES + "/" + legacyId).with(TestJwts.engineer()))
                .andExpect(jsonPath("$.caseNumber").value("PPF-2025-00042"))
                .andExpect(jsonPath("$.source").value("LEGACY_IMPORT"))
                .andExpect(jsonPath("$.status").value("CONDITIONALLY_APPROVED"))
                .andExpect(jsonPath("$.lastDecision.conditionDueDate")
                        .value(LocalDate.now().plusDays(30).toString()))
                .andExpect(jsonPath("$.lastDecision.signature.status").value("NOT_SIGNED"))
                .andExpect(jsonPath("$.submissions[0].signature.status").value("NOT_SIGNED"))
                .andExpect(jsonPath("$.timeline.length()").value(4));
        mvc.perform(get(CASES + "/" + legacyId + "/submissions/1").with(TestJwts.engineer()))
                .andExpect(jsonPath("$.elements[0].documents.length()").value(2))
                .andExpect(jsonPath("$.elements[1].values[0].cpk").value(1.2))
                .andExpect(jsonPath("$.elements[1].finding.result").value("ACCEPTED_WITH_CONDITION"));
        mvc.perform(get(CASES + "/" + legacyId + "/submissions/1/payload").with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("X-Signature"));

        String draftId = jdbc.queryForObject(
                "select case_number from ap_case where exchange_id = '11111111-2222-5333-8444-555555555503'",
                String.class);
        assertThat(draftId).startsWith("PPF-" + LocalDate.now().getYear() + "-");

        // counter continues above seeded numbers
        Long counter2025 =
                jdbc.queryForObject("select last_value from ap_case_number_counter where year = 2025", Long.class);
        assertThat(counter2025).isGreaterThanOrEqualTo(42L);
        Long counter2026 =
                jdbc.queryForObject("select last_value from ap_case_number_counter where year = 2026", Long.class);
        assertThat(counter2026).isGreaterThanOrEqualTo(901L);

        mvc.perform(get(CASES + "/stats").with(TestJwts.engineer()))
                .andExpect(jsonPath("$.approvalRate").isNumber())
                .andExpect(jsonPath("$.byPartner[*].bpnl").value(hasItem("BPNLFIXTURE00001")));
    }

    @Test
    void excelFormImportWithSeedTemplate() throws Exception {
        String caseId = id(SUPPLIER_CASE);
        byte[] filled = Files.readAllBytes(Path.of("src/test/resources/forms/ppf-formblatt-v1-beispiel-tier2.xlsx"));
        var supplier = TestJwts.user("tier1.quality", "SUPPLIER_QUALITY_MANAGER");
        String base = CASES + "/" + caseId + "/submissions/draft/excel-import";
        mvc.perform(get(CASES + "/" + caseId + "/submissions/draft").with(supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].documents[0].fileName").value("deckblatt.pdf"))
                .andExpect(jsonPath("$.completeness.requiredTotal").value(1));

        mvc.perform(multipart(base + "/preview")
                        .file(new MockMultipartFile("file", "formblatt.xlsx", "application/octet-stream", filled))
                        .with(supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateCode").value("PPF-FORM-V1"))
                .andExpect(jsonPath("$.header.supplierPartNumber.value").value("T2-88-1200"))
                .andExpect(jsonPath("$.header.supplierPartNumber.cell").value("PPF-Bericht!C6"))
                .andExpect(jsonPath("$.unknownCodes").value(hasItem("PPF-01")))
                .andExpect(jsonPath("$.warnings[*].code").value(hasItem("PART_NUMBER_MISMATCH")));

        // generated workbook with the fixture codes
        byte[] workbook = workbook(Map.of("FX-01", "n.i.O.", "FX-02", "vielleicht"), "FX-01");
        mvc.perform(multipart(base + "/preview")
                        .file(new MockMultipartFile("file", "f.xlsx", "application/octet-stream", workbook))
                        .with(supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].code").value("FX-01"))
                .andExpect(jsonPath("$.elements[0].selfAssessment").value("NOK"))
                .andExpect(jsonPath("$.elements[0].status").value("OK"))
                .andExpect(jsonPath("$.elements[0].cell").value("PPF-Bericht!A14"))
                .andExpect(jsonPath("$.elements[1].status").value("ERROR"))
                .andExpect(jsonPath("$.elements[1].messages[0].code").value("UNKNOWN_SELF_ASSESSMENT"));

        mvc.perform(multipart(base + "/apply")
                        .file(new MockMultipartFile("file", "formblatt-fx.xlsx", "application/octet-stream", workbook))
                        .file(new MockMultipartFile(
                                "selection",
                                "",
                                "application/json",
                                "{\"elementCodes\":[],\"overwrite\":true}".getBytes(StandardCharsets.UTF_8)))
                        .param("templateCode", "PPF-FORM-V1")
                        .with(supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].selfAssessment").value("NOK"))
                .andExpect(jsonPath("$.elements[0].comment").value("Kommentar FX-01"))
                .andExpect(jsonPath("$.elements[0].documents[*].fileName").value(hasItem("formblatt-fx.xlsx")))
                .andExpect(jsonPath("$.elements[1].selfAssessment").doesNotExist());

        mvc.perform(multipart(base + "/preview")
                        .file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", "nope".getBytes()))
                        .with(supplier))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNREADABLE_FILE"));
        mvc.perform(multipart(base + "/preview")
                        .file(new MockMultipartFile(
                                "file", "x.xlsx", "application/octet-stream", workbook(Map.of(), "other")))
                        .with(supplier))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FORM_TEMPLATE"));
        mvc.perform(multipart(base + "/preview")
                        .file(new MockMultipartFile("file", "f.xlsx", "application/octet-stream", workbook))
                        .with(TestJwts.engineer()))
                .andExpect(status().isForbidden());

        // the seeded draft (FX-01 with document) can be completed and submitted
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                CASES + "/" + caseId + "/submissions/draft/elements/FX-01")
                        .with(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selfAssessment\":\"OK\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(CASES + "/" + caseId + "/submissions/draft/submit")
                        .with(supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature.status").value("VALID"));
    }

    private String id(String exchangeId) {
        return jdbc.queryForObject("select id from ap_case where exchange_id = ?::uuid", String.class, exchangeId);
    }

    private static byte[] workbook(Map<String, String> assessments, String marker) throws Exception {
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("PPF-Bericht");
            sheet.createRow(0)
                    .createCell(0)
                    .setCellValue("other".equals(marker) ? "Kein Formblatt" : "msg PPAP Formblatt PPF v1");
            sheet.createRow(5).createCell(2).setCellValue("FX-4711");
            int row = 13;
            for (String code : assessments.keySet().stream().sorted().toList()) {
                var r = sheet.createRow(row++);
                r.createCell(0).setCellValue(code);
                r.createCell(3).setCellValue(assessments.get(code));
                r.createCell(4).setCellValue("Kommentar " + code);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
