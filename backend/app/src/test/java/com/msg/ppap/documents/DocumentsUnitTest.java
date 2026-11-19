package com.msg.ppap.documents;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.documents.domain.AllowedMediaTypes;
import com.msg.ppap.documents.domain.Document;
import com.msg.ppap.documents.domain.FileNames;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentsUnitTest {

    @Test
    void sanitizesFileNames() {
        assertThat(FileNames.sanitize("C:\\temp\\..\\a.pdf", "x")).isEqualTo("a.pdf");
        assertThat(FileNames.sanitize("../../etc/passwd", "x")).isEqualTo("passwd");
        assertThat(FileNames.sanitize(".hidden", "x")).isEqualTo("hidden");
        assertThat(FileNames.sanitize("a\u0000b\u202e.pdf", "x")).isEqualTo("ab.pdf");
        assertThat(FileNames.sanitize("   ", "fallback")).isEqualTo("fallback");
        assertThat(FileNames.sanitize(null, "fallback")).isEqualTo("fallback");
        String longName = "n".repeat(300) + ".pdf";
        assertThat(FileNames.sanitize(longName, "x"))
                .hasSize(FileNames.MAX_LENGTH)
                .endsWith(".pdf");
    }

    @Test
    void whitelist() {
        assertThat(AllowedMediaTypes.isAllowed("text/plain; charset=UTF-8")).isTrue();
        assertThat(AllowedMediaTypes.isAllowed("APPLICATION/PDF")).isTrue();
        assertThat(AllowedMediaTypes.isAllowed("text/html")).isFalse();
        assertThat(AllowedMediaTypes.isAllowed(null)).isFalse();
    }

    @Test
    void contentAddressedKey() {
        var doc = new Document(
                UUID.randomUUID(), "BPNL1", "a.pdf", "application/pdf", 1, "a".repeat(64), Instant.EPOCH, "u");
        assertThat(doc.storageKey()).isEqualTo("BPNL1/" + "a".repeat(64));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new Document(UUID.randomUUID(), "T", "a", "b", 1, "XYZ", Instant.EPOCH, "u"));
    }
}
