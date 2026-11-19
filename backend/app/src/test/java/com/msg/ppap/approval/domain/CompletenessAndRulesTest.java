package com.msg.ppap.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompletenessAndRulesTest {

    static final LocalDate TODAY = CaseFixtures.TODAY;

    @ParameterizedTest(name = "{0} {1} sa={2} comment={3} docs={4} values={5} -> {6}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            REQUIRED   | DOCUMENT            | -   | -   | 0 | -     | MISSING_SELF_ASSESSMENT,DOCUMENT_REQUIRED
            REQUIRED   | DOCUMENT            | OK  | -   | 1 | -     | -
            REQUIRED   | DOCUMENT            | NOK | -   | 1 | -     | COMMENT_REQUIRED
            REQUIRED   | DOCUMENT            | NA  | -   | 0 | -     | COMMENT_REQUIRED
            REQUIRED   | DOCUMENT            | NA  | why | 0 | -     | -
            REQUIRED   | DOCUMENT            | NOK | bad | 0 | -     | DOCUMENT_REQUIRED
            REQUIRED   | VALUES              | OK  | -   | 0 | -     | VALUES_REQUIRED
            REQUIRED   | VALUES              | OK  | -   | 0 | bad   | VALUE_SCHEMA_VIOLATION
            REQUIRED   | VALUES              | OK  | -   | 0 | good  | -
            REQUIRED   | VALUES              | NA  | why | 0 | -     | -
            REQUIRED   | DOCUMENT_AND_VALUES | OK  | -   | 0 | -     | DOCUMENT_REQUIRED,VALUES_REQUIRED
            REQUIRED   | DOCUMENT_AND_VALUES | OK  | -   | 1 | good  | -
            ON_REQUEST | DOCUMENT            | -   | -   | 0 | -     | -
            ON_REQUEST | DOCUMENT            | -   | x   | 0 | -     | MISSING_SELF_ASSESSMENT,DOCUMENT_REQUIRED
            ON_REQUEST | VALUES              | OK  | -   | 0 | good  | -
            """)
    void completenessRules(
            Requirement requirement,
            EvidenceType type,
            SelfAssessment assessment,
            String comment,
            int documents,
            String values,
            String expected) {
        var element = new RequestedElement(
                "E1", requirement, null, CaseFixtures.snapshot(type, type.needsValues() ? CaseFixtures.SCHEMA : null));
        Object valueTree = values == null ? null : values.equals("good") ? Map.of("quantity", 1) : Map.of();
        var evidence = new ElementEvidence(
                "E1",
                requirement,
                assessment,
                valueTree,
                comment,
                documents == 0 ? List.of() : List.of(CaseFixtures.document("a.pdf")));
        Completeness result = Completeness.evaluate(List.of(element), Map.of("E1", evidence), CaseFixtures.VALIDATOR);
        List<String> codes =
                result.issues().stream().map(Completeness.Issue::code).toList();
        if (expected == null) {
            assertThat(codes).isEmpty();
            assertThat(result.complete()).isTrue();
        } else {
            assertThat(codes).containsExactly(expected.split(","));
            assertThat(result.issues("E1")).hasSize(codes.size());
        }
        assertThat(result.requiredTotal()).isEqualTo(requirement == Requirement.REQUIRED ? 1 : 0);
    }

    @Test
    void schemaViolationsCarryPointer() {
        var element = new RequestedElement(
                "E1", Requirement.REQUIRED, null, CaseFixtures.snapshot(EvidenceType.VALUES, CaseFixtures.SCHEMA));
        var evidence = new ElementEvidence("E1", Requirement.REQUIRED, SelfAssessment.OK, Map.of(), null, List.of());
        Completeness c = Completeness.evaluate(List.of(element), Map.of("E1", evidence), CaseFixtures.VALIDATOR);
        assertThat(c.issues().getFirst().pointer()).isEqualTo("/quantity");
        assertThat(c.issues("other")).isEmpty();
    }

    @Test
    void missingEvidenceCountsAsEmpty() {
        var element = new RequestedElement(
                "E1", Requirement.REQUIRED, null, CaseFixtures.snapshot(EvidenceType.DOCUMENT, null));
        Completeness c = Completeness.evaluate(List.of(element), Map.of(), CaseFixtures.VALIDATOR);
        assertThat(c.requiredDone()).isZero();
        assertThat(c.complete()).isFalse();
    }

    @Test
    void decisionRules() {
        var notAccepted = new ElementFinding("A", FindingResult.NOT_ACCEPTED, "reason");
        var withoutReason = new ElementFinding("B", FindingResult.NOT_ACCEPTED, null);
        var accepted = new ElementFinding("C", FindingResult.ACCEPTED, null);

        assertThatCode(() -> DecisionRules.check(DecisionResult.APPROVED, List.of(), null, List.of(accepted), TODAY))
                .doesNotThrowAnyException();
        assertThatThrownBy(() ->
                        DecisionRules.check(DecisionResult.APPROVED, List.of(), null, List.of(notAccepted), TODAY))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        ex -> assertThat(ex.violations().getFirst().field()).isEqualTo("elementFindings.A"));
        assertThatCode(() -> DecisionRules.check(DecisionResult.REJECTED, List.of(), null, List.of(notAccepted), TODAY))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DecisionRules.check(
                        DecisionResult.REJECTED, List.of(), null, List.of(notAccepted, withoutReason), TODAY))
                .isInstanceOfSatisfying(ValidationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApprovalErrors.REJECTION_REASON_REQUIRED);
                    assertThat(ex.violations().getFirst().field()).isEqualTo("elementFindings.B.comment");
                });
        assertThatThrownBy(
                        () -> DecisionRules.check(DecisionResult.REJECTED, List.of(), null, List.of(accepted), TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REJECTION_REASON_REQUIRED);
        assertThatCode(() -> DecisionRules.check(
                        DecisionResult.CONDITIONALLY_APPROVED, List.of("fix"), TODAY, List.of(), TODAY))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> DecisionRules.check(
                        DecisionResult.CONDITIONALLY_APPROVED, List.of("fix"), null, List.of(), TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONDITIONS_REQUIRED);
        assertThatThrownBy(() -> DecisionRules.check(
                        DecisionResult.CONDITIONALLY_APPROVED, List.of("fix"), TODAY.minusDays(1), List.of(), TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONDITIONS_REQUIRED);
        assertThatThrownBy(() ->
                        DecisionRules.check(DecisionResult.CONDITIONALLY_APPROVED, List.of(), TODAY, List.of(), TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONDITIONS_REQUIRED);
    }

    @Test
    void formTemplateMapsSelfAssessments() {
        var template = new FormTemplate(
                "T",
                null,
                null,
                new FormTemplate.Marker("S", "A1", "Marker"),
                null,
                new FormTemplate.ElementTable("S", 2, 10, Map.of("code", "A", "selfAssessment", "B")),
                Map.of("i.O.", "OK", "n.i.O.", "NOK", "entfällt", "na"),
                true);
        assertThat(template.selfAssessment("i.O.")).contains(SelfAssessment.OK);
        assertThat(template.selfAssessment(" N.I.O. ")).contains(SelfAssessment.NOK);
        assertThat(template.selfAssessment("entfällt")).contains(SelfAssessment.NA);
        assertThat(template.selfAssessment(" ")).isEmpty();
        assertThatThrownBy(() -> template.selfAssessment("vielleicht")).isInstanceOf(IllegalArgumentException.class);
        assertThat(template.marker().matches(" marker ")).isTrue();
        assertThat(template.marker().matches(null)).isFalse();
        assertThat(template.elementTable().cell("comment", 3)).isNull();
        assertThat(template.elementTable().cell("code", 3)).isEqualTo("A3");
        assertThat(template.nameEn()).isEqualTo("T");
        assertThatThrownBy(() -> new FormTemplate.ElementTable("S", 5, 2, Map.of("code", "A")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormTemplate.ElementTable("S", 1, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
