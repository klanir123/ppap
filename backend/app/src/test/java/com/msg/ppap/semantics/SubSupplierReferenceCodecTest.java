package com.msg.ppap.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.semantics.adapter.out.schema.SchemaValidatorAccess;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.application.ExchangeCodecService;
import com.msg.ppap.semantics.application.v0_1.ExchangeMapperV0_1;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sub-supplier references in {@code PpapSubmission} (UC-09): SAMM-generated schema, mapping both ways. */
class SubSupplierReferenceCodecTest {

    static final UUID CHILD = UUID.fromString("5b8ca33e-609f-55f1-9a79-77031bf2c0a5");

    final ExchangeCodec codec = new ExchangeCodecService(new ExchangeMapperV0_1(), SchemaValidatorAccess.validator());

    static SubmissionMessage withReferences(List<SubmissionMessage.SubSupplierReference> references) {
        SubmissionMessage base = ExchangeCodecTest.submission();
        return new SubmissionMessage(
                base.exchangeId(),
                base.caseNumber(),
                base.revision(),
                base.customerBpnl(),
                base.supplierBpnl(),
                base.customerPartNumber(),
                base.supplierPartNumber(),
                base.reasonCode(),
                base.levelCode(),
                base.elements(),
                references,
                base.submittedAt(),
                base.submittedBy());
    }

    static SubmissionMessage.SubSupplierReference reference(String result, LocalDate dueDate, String name) {
        return new SubmissionMessage.SubSupplierReference(
                "BPNL000000TIER02",
                name,
                CHILD,
                "PPF-2026-00012",
                "T1-520000-00",
                null,
                "T2-88-1200",
                1,
                result,
                Instant.parse("2026-11-18T09:00:00.500Z"),
                "sha256:" + "c".repeat(64),
                dueDate);
    }

    @Test
    void referencesRoundTripThroughTheSignedPayload() {
        var refs = List.of(
                reference("APPROVED", null, "Demo-Tier2 KG"),
                reference("CONDITIONALLY_APPROVED", LocalDate.parse("2026-12-31"), " "));
        EncodedPayload encoded = codec.encodeSubmission(withReferences(refs));
        assertThat(encoded.text())
                .contains("\"subSupplierReferences\":[{")
                .contains("\"decisionHash\":\"sha256:" + "c".repeat(64) + "\"")
                .contains("\"conditionDueDate\":\"2026-12-31\"")
                .contains("\"subSupplierName\":\"Demo-Tier2 KG\"")
                .doesNotContain("\"partName\"")
                .doesNotContain("null");
        var decoded = codec.decodeSubmission(encoded.text().getBytes(StandardCharsets.UTF_8));
        assertThat(decoded.payload().hash()).isEqualTo(encoded.hash());
        assertThat(decoded.message().subSupplierReferences()).hasSize(2);
        var first = decoded.message().subSupplierReferences().getFirst();
        assertThat(first.exchangeId()).isEqualTo(CHILD);
        assertThat(first.decisionResult()).isEqualTo("APPROVED");
        assertThat(first.decidedAt()).isEqualTo(Instant.parse("2026-11-18T09:00:00.500Z"));
        assertThat(first.conditionDueDate()).isNull();
        assertThat(decoded.message().subSupplierReferences().get(1).subSupplierName())
                .isNull();
        assertThat(decoded.message().subSupplierReferences().get(1).conditionDueDate())
                .isEqualTo(LocalDate.parse("2026-12-31"));
    }

    @Test
    void rejectedResultsAndMalformedHashesViolateTheSchema() {
        assertThatThrownBy(() -> codec.encodeSubmission(withReferences(List.of(reference("REJECTED", null, null)))))
                .isInstanceOf(PayloadValidationException.class);
        var badHash = new SubmissionMessage.SubSupplierReference(
                "BPNL000000TIER02",
                null,
                CHILD,
                "PPF-1",
                "T1",
                null,
                null,
                1,
                "APPROVED",
                Instant.parse("2026-11-18T09:00:00Z"),
                "md5:abc",
                null);
        assertThatThrownBy(() -> codec.encodeSubmission(withReferences(List.of(badHash))))
                .isInstanceOf(PayloadValidationException.class);
    }
}
