package com.msg.ppap.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.ppap.semantics.api.PayloadHash;
import com.msg.ppap.support.AbstractIntegrationTest;
import com.msg.ppap.support.TestJwts;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

class DocumentsIntegrationTest extends AbstractIntegrationTest {

    static final byte[] PNG = {
        (byte) 0x89,
        'P',
        'N',
        'G',
        '\r',
        '\n',
        0x1a,
        '\n',
        0,
        0,
        0,
        0x0d,
        'I',
        'H',
        'D',
        'R',
        0,
        0,
        0,
        1,
        0,
        0,
        0,
        1,
        8,
        6,
        0,
        0,
        0
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void uploadsDetectsTypeHashesAndServesSafely() throws Exception {
        byte[] pdf = ("%PDF-1.4\n%" + UUID.randomUUID() + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        String sha = PayloadHash.sha256Hex(pdf);
        String body = mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "../Prüf bericht \"v1\".pdf", "text/plain", pdf))
                        .with(TestJwts.user("tier1.quality", "SUPPLIER_QUALITY_MANAGER")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.fileName").value("Prüf bericht v1.pdf"))
                .andExpect(jsonPath("$.size").value(pdf.length))
                .andExpect(jsonPath("$.sha256").value(sha))
                .andExpect(jsonPath("$.uploadedBy").value("tier1.quality"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String id = json.readTree(body).get("id").asString();

        mvc.perform(get("/api/v1/documents/" + id).with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").value(sha));
        var content = mvc.perform(get("/api/v1/documents/" + id + "/content")
                        .param("disposition", "inline")
                        .with(TestJwts.engineer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"Pr_f bericht v1.pdf\"; filename*=UTF-8''Pr%C3%BCf%20bericht%20v1.pdf"))
                .andReturn();
        assertThat(content.getResponse().getContentAsByteArray()).isEqualTo(pdf);

        // same content again: new metadata, same content-addressed object
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "copy.pdf", "application/pdf", pdf))
                        .with(TestJwts.engineer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value(sha));
        assertThat(jdbc.queryForObject(
                        "select count(distinct storage_key) from doc_document where sha256 = ?", Integer.class, sha))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/documents/" + UUID.randomUUID()).with(TestJwts.engineer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsWhitelistedTypes() throws Exception {
        upload("bild.png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("image/png"));
        upload("liste.csv", "a;b\n1;2\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("text/csv"));
        upload("notiz.txt", "Hallo".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("text/plain"));
        upload("paket.zip", zip())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("application/zip"));
    }

    @Test
    void rejectsUnsupportedEmptyAndTooLargeFiles() throws Exception {
        byte[] exe = new byte[256];
        exe[0] = 'M';
        exe[1] = 'Z';
        upload("harmlos.pdf", exe)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        upload("seite.txt", "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType());
        upload("leer.pdf", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));
        byte[] large = new byte[2 * 1024 * 1024 + 1];
        large[0] = '%';
        upload("gross.pdf", large)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PNG))
                        .with(TestJwts.manager()))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions upload(String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content))
                .with(TestJwts.user("tier1.quality", "SUPPLIER_QUALITY_MANAGER")));
    }

    private static byte[] zip() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("a.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
