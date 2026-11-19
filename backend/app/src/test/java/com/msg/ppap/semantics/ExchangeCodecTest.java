package com.msg.ppap.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.semantics.adapter.out.schema.SchemaValidatorAccess;
import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.DocumentReference;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.PayloadValidationException;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.application.ExchangeCodecService;
import com.msg.ppap.semantics.application.v0_1.ExchangeMapperV0_1;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExchangeCodecTest {

    static final UUID EXCHANGE_ID = UUID.fromString("4a8ca33e-609f-55f1-9a79-77031bf2c0a4");
    static final Instant AT = Instant.parse("2026-11-19T10:15:30.123Z");

    final ExchangeCodec codec = new ExchangeCodecService(new ExchangeMapperV0_1(), SchemaValidatorAccess.validator());

    static SubmissionMessage submission() {
        return new SubmissionMessage(
                EXCHANGE_ID,
                "PPF-2026-00003",
                1,
                "BPNL00000003AYRE",
                "BPNL000000TIER01",
                "OEM-4711",
                "T1-4711",
                "NEW_PART",
                "VDA-2",
                List.of(
                        new SubmissionMessage.Element(
                                "PPF-11",
                                "REQUIRED",
                                "NOK",
                                List.of(Map.of("characteristic", "A1", "nominal", 12.0, "measured", 12.02)),
                                "Merkmal A2 knapp außerhalb",
                                List.of(new DocumentReference(
                                        "messprotokoll.pdf", "application/pdf", 1234, "a".repeat(64)))),
                        new SubmissionMessage.Element("PPF-01", "REQUIRED", "OK", null, " ", List.of())),
                List.of(),
                AT,
                "tier1.quality");
    }

    @Test
    void submissionRoundTripIsCanonicalAndStable() {
        EncodedPayload encoded = codec.encodeSubmission(submission());
        assertThat(encoded.modelUrn()).isEqualTo(ExchangeModels.PPAP_SUBMISSION);
        assertThat(encoded.text())
                .startsWith("{\"elements\":[")
                // structured values are canonical JSON text (SAMM JsonText)
                .contains(
                        "\"values\":\"[{\\\"characteristic\\\":\\\"A1\\\",\\\"measured\\\":12.02,\\\"nominal\\\":12}]\"")
                .doesNotContain("null")
                .doesNotContain("\"comment\":\" \"");
        assertThat(encoded.hash()).matches("^sha256:[0-9a-f]{64}$");
        assertThat(codec.encodeSubmission(submission())).isEqualTo(encoded);

        // pretty-printed, reordered input decodes to the same canonical payload and hash
        String reformatted = tools.jackson.databind.json.JsonMapper.builder()
                .enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                .build()
                .writeValueAsString(
                        tools.jackson.databind.json.JsonMapper.builder().build().readTree(encoded.text()));
        var decoded = codec.decodeSubmission(reformatted.getBytes(StandardCharsets.UTF_8));
        assertThat(decoded.payload().hash()).isEqualTo(encoded.hash());
        assertThat(decoded.message().revision()).isEqualTo(1);
        assertThat(decoded.message().submittedAt()).isEqualTo(AT);
        assertThat(decoded.message()
                        .elements()
                        .getFirst()
                        .documents()
                        .getFirst()
                        .sha256())
                .isEqualTo("a".repeat(64));
        assertThat(decoded.message().elements().get(1).values()).isNull();
        assertThat(decoded.message().elements().getFirst().values())
                .isEqualTo(List.of(Map.of("characteristic", "A1", "nominal", 12, "measured", 12.02)));
        assertThat(encoded.toString()).contains(encoded.hash());
        assertThat(encoded.canonical()).isEqualTo(encoded.text().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void invalidSubmissionIsRejectedWithPointers() {
        var invalid = new SubmissionMessage(
                EXCHANGE_ID,
                "PPF-1",
                0,
                "NOT-A-BPNL",
                "BPNL000000TIER01",
                "OEM-4711",
                "T1-4711",
                "NEW_PART",
                "VDA-2",
                List.of(new SubmissionMessage.Element("PPF-01", "REQUIRED", "MAYBE", null, null, List.of())),
                List.of(),
                AT,
                "x");
        assertThatThrownBy(() -> codec.encodeSubmission(invalid))
                .isInstanceOfSatisfying(PayloadValidationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("PAYLOAD_SCHEMA_VIOLATION");
                    assertThat(ex.violations())
                            .extracting(v -> v.field())
                            .contains("/header/revision", "/header/customerBpnl", "/elements/0/selfAssessment");
                });
    }

    @Test
    void receivedPayloadsAreValidated() {
        assertThatThrownBy(() -> codec.decodeSubmission("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> codec.decodeSubmission("[1]".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
        assertThatThrownBy(() -> codec.decodeSubmission("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PayloadValidationException.class);
        String extra = codec.encodeSubmission(submission()).text().replaceFirst("\\{", "{\"unexpected\":true,");
        assertThatThrownBy(() -> codec.decodeSubmission(extra.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        PayloadValidationException.class,
                        ex -> assertThat(ex.violations().getFirst().code()).isEqualTo("SCHEMA_ADDITIONALPROPERTIES"));
        String missing = "{\"header\":{}}";
        assertThatThrownBy(() -> codec.decodeSubmission(missing.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(
                        PayloadValidationException.class,
                        ex -> assertThat(ex.violations())
                                .extracting(v -> v.field())
                                .contains("/elements", "/header/exchangeId"));
    }

    @Test
    void decisionRoundTrip() {
        var decision = new DecisionMessage(
                EXCHANGE_ID,
                "PPF-2026-00001",
                2,
                "sha256:" + "b".repeat(64),
                "CONDITIONALLY_APPROVED",
                List.of("Nachweis Werkzeugkorrektur"),
                LocalDate.parse("2026-12-19"),
                "",
                List.of(new DecisionMessage.Finding("PPF-11", "ACCEPTED_WITH_CONDITION", "Merkmal 7")),
                AT,
                "oem.quality");
        EncodedPayload encoded = codec.encodeDecision(decision);
        assertThat(encoded.text()).contains("\"conditions\":[{\"text\":\"Nachweis Werkzeugkorrektur\"}]");
        var decoded = codec.decodeDecision(encoded.canonical());
        assertThat(decoded.message().conditionDueDate()).isEqualTo(LocalDate.parse("2026-12-19"));
        assertThat(decoded.message().comment()).isNull();
        assertThat(decoded.message().elementFindings()).hasSize(1);
        assertThat(decoded.payload().hash()).isEqualTo(encoded.hash());

        var wrongHash = new DecisionMessage(
                EXCHANGE_ID, "X", 1, "md5:1", "APPROVED", List.of(), null, null, List.of(), AT, "u");
        assertThatThrownBy(() -> codec.encodeDecision(wrongHash)).isInstanceOf(PayloadValidationException.class);
    }

    @Test
    void requestRoundTrip() {
        var request = new RequestMessage(
                EXCHANGE_ID,
                "PPF-2026-00001",
                "BPNL00000003AYRE",
                "BPNL000000TIER01",
                "OEM-4711",
                null,
                "Türgriff",
                new RequestMessage.CodeName("NEW_PART", "Neuteil", "New part"),
                new RequestMessage.CodeName("VDA-2", "Stufe 2", "Level 2"),
                LocalDate.parse("2026-12-15"),
                null,
                AT,
                "oem.quality",
                List.of(new RequestMessage.Element(
                        "PPF-17",
                        "REQUIRED",
                        "5 Teile",
                        new RequestMessage.Snapshot(
                                "Erstmuster", "Initial samples", "VALUES", Map.of("type", "object")))),
                List.of());
        EncodedPayload encoded = codec.encodeRequest(request);
        var decoded = codec.decodeRequest(encoded.canonical());
        assertThat(decoded.message().supplierPartNumber()).isNull();
        assertThat(decoded.message().elements().getFirst().snapshot().valueSchema())
                .containsEntry("type", "object");
        assertThat(decoded.message().reason().nameEn()).isEqualTo("New part");

        var empty = new RequestMessage(
                EXCHANGE_ID,
                "N",
                "BPNL00000003AYRE",
                "BPNL000000TIER01",
                "P",
                null,
                null,
                request.reason(),
                request.level(),
                request.dueDate(),
                null,
                AT,
                "u",
                List.of(),
                List.of());
        assertThatThrownBy(() -> codec.encodeRequest(empty)).isInstanceOf(PayloadValidationException.class);
    }
}
