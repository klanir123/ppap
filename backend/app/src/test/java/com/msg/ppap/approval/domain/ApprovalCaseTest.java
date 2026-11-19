package com.msg.ppap.approval.domain;

import static com.msg.ppap.approval.domain.CaseFixtures.CQE;
import static com.msg.ppap.approval.domain.CaseFixtures.NOW;
import static com.msg.ppap.approval.domain.CaseFixtures.SI;
import static com.msg.ppap.approval.domain.CaseFixtures.SQM;
import static com.msg.ppap.approval.domain.CaseFixtures.TODAY;
import static com.msg.ppap.approval.domain.CaseFixtures.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.VersionConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalCaseTest {

    @Test
    void customerCreatesEditsAndSendsDraft() {
        ApprovalCase c = CaseFixtures.draft();
        assertThat(c.status()).isEqualTo(CaseStatus.DRAFT);
        assertThat(c.source()).isEqualTo(CaseSource.LOCAL);
        assertThat(c.ownRole()).isEqualTo(CaseRole.CUSTOMER);
        assertThat(c.customerPartNumber()).isEqualTo("OEM-4711");
        assertThat(c.supplierPartNumber()).isEqualTo("T1-4711");
        assertThat(c.request().requestedAt()).isNull();

        c.updateDraft(
                new CatalogRef("TOOLING", null, null),
                new CatalogRef("VDA-3", "S3", "L3"),
                TODAY.plusDays(20),
                "new",
                CQE,
                NOW);
        assertThat(c.header().reason().nameEn()).isEqualTo("TOOLING");
        c.replaceRequestElements(CaseFixtures.elements().subList(0, 2), CQE, NOW);
        DocumentRef attachment = CaseFixtures.document("zeichnung.pdf");
        c.addAttachment(attachment, CQE, NOW);
        c.addAttachment(attachment, CQE, NOW);
        assertThat(c.request().attachments()).hasSize(1);
        c.removeAttachment(attachment.documentId(), CQE, NOW);
        assertThatThrownBy(() -> c.removeAttachment(UUID.randomUUID(), CQE, NOW))
                .isInstanceOf(NotFoundException.class);

        c.send(true, CQE, NOW, TODAY);
        assertThat(c.status()).isEqualTo(CaseStatus.REQUESTED);
        assertThat(c.request().requestedBy()).isEqualTo("oem.quality");
        assertThat(c.timeline()).extracting(TimelineEntry::action).containsExactly(CaseAction.CREATE, CaseAction.SEND);
        assertThat(c.timeline().getFirst().from()).isNull();
        List<CaseChange> changes = c.pullChanges();
        assertThat(changes).extracting(CaseChange::action).contains(CaseAction.EDIT, CaseAction.SEND);
        assertThat(c.pullChanges()).isEmpty();

        assertThatThrownBy(
                        () -> c.updateDraft(c.header().reason(), c.header().level(), TODAY.plusDays(3), null, CQE, NOW))
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void sendGuards() {
        ApprovalCase noElements =
                ApprovalCase.createDraft("PPF-2026-00009", CaseFixtures.header(TODAY.plusDays(5)), List.of(), CQE, NOW);
        assertThatThrownBy(() -> noElements.send(true, CQE, NOW, TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.NO_ELEMENTS);
        ApprovalCase dueToday = ApprovalCase.createDraft(
                "PPF-2026-00010", CaseFixtures.header(TODAY), CaseFixtures.elements(), CQE, NOW);
        assertThatThrownBy(() -> dueToday.send(true, CQE, NOW, TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.DUE_DATE_IN_PAST);
        ApprovalCase c = CaseFixtures.draft();
        assertThatThrownBy(() -> c.send(false, CQE, NOW, TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.PARTNER_NOT_EXCHANGE_READY);
        assertThatThrownBy(() -> c.send(true, SQM, NOW, TODAY)).hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThat(c.status()).isEqualTo(CaseStatus.DRAFT);
    }

    @Test
    void cancelRequiresReasonAndRespectsSource() {
        ApprovalCase c = CaseFixtures.draft();
        assertThatThrownBy(() -> c.cancel(" ", CQE, NOW)).isInstanceOf(ValidationException.class);
        c.cancel("duplicate", CQE, NOW);
        assertThat(c.status()).isEqualTo(CaseStatus.CANCELLED);
        assertThat(c.timeline().getLast().comment()).isEqualTo("duplicate");

        ApprovalCase manual = CaseFixtures.manual();
        manual.cancel("customer withdrew", SQM, NOW);
        assertThat(manual.status()).isEqualTo(CaseStatus.CANCELLED);

        ApprovalCase received = ApprovalCase.receiveRequest(
                "PPF-2026-00003",
                UUID.randomUUID(),
                CaseFixtures.header(TODAY.plusDays(5)),
                new ApprovalRequest(NOW, "oem", CaseFixtures.elements(), List.of()),
                Actor.exchange(),
                NOW);
        assertThatThrownBy(() -> received.cancel("no", SQM, NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.WRONG_CASE_ROLE);
        assertThatThrownBy(() -> ApprovalCase.receiveRequest(
                        "X",
                        UUID.randomUUID(),
                        CaseFixtures.header(TODAY),
                        new ApprovalRequest(NOW, "oem", List.of(), List.of()),
                        Actor.exchange(),
                        NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.NO_ELEMENTS);
    }

    @Test
    void supplierFillsAndSubmitsCompleteRevision() {
        ApprovalCase c = CaseFixtures.manual();
        assertThat(c.allowedActions(SQM, CasePolicy.DEFAULT)).containsExactly(CaseAction.CANCEL, CaseAction.START_WORK);
        c.startWork(SQM, NOW);
        Submission draft = c.draftSubmission();
        assertThat(draft.revision()).isEqualTo(1);
        assertThat(draft.elements()).extracting(ElementEvidence::code).containsExactly("PPF-01", "PPF-17", "PPF-11");

        Completeness initial = c.completeness(draft, VALIDATOR);
        assertThat(initial.complete()).isFalse();
        assertThat(initial.requiredTotal()).isEqualTo(2);
        assertThat(initial.requiredDone()).isZero();

        // invalid values are stored in the draft and reported as completeness issue
        c.updateEvidence("PPF-17", SelfAssessment.OK, Map.of("x", 1), null, SQM, NOW);
        assertThat(c.draftSubmission().evidence("PPF-17").orElseThrow().values())
                .isEqualTo(Map.of("x", 1));
        assertThat(c.completeness(c.draftSubmission(), VALIDATOR).issues("PPF-17"))
                .extracting(Completeness.Issue::code, Completeness.Issue::pointer)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ApprovalErrors.VALUE_SCHEMA_VIOLATION, "/quantity"));
        assertThatThrownBy(() -> c.updateEvidence("PPF-99", SelfAssessment.OK, null, null, SQM, NOW))
                .isInstanceOf(NotFoundException.class);

        assertThatThrownBy(() -> c.submit(true, VALIDATOR, (ca, s) -> null, SQM, NOW))
                .isInstanceOfSatisfying(ValidationException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ApprovalErrors.INCOMPLETE_SUBMISSION);
                    assertThat(ex.violations())
                            .extracting(v -> v.field() + ":" + v.code())
                            .contains(
                                    "elements.PPF-01:MISSING_SELF_ASSESSMENT",
                                    "elements.PPF-01:DOCUMENT_REQUIRED",
                                    "elements.PPF-17.values/quantity:VALUE_SCHEMA_VIOLATION");
                });

        ApprovalCase complete = CaseFixtures.completeSupplierCase();
        DocumentRef doc = complete.draftSubmission()
                .evidence("PPF-01")
                .orElseThrow()
                .documents()
                .getFirst();
        complete.attachEvidenceDocument("PPF-01", doc, SQM, NOW);
        assertThat(complete.draftSubmission().evidence("PPF-01").orElseThrow().documents())
                .hasSize(1);
        assertThatThrownBy(() -> complete.submit(false, VALIDATOR, (ca, s) -> null, SQM, NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONFIRMATION_REQUIRED);

        Submission submitted = complete.submit(
                true,
                VALIDATOR,
                (ca, s) -> {
                    assertThat(s.status()).isEqualTo(SubmissionStatus.SUBMITTED);
                    assertThat(s.submittedBy()).isEqualTo("tier1.quality");
                    return CaseFixtures.seal("{}", SignatureStatus.VALID);
                },
                SQM,
                NOW);
        assertThat(complete.status()).isEqualTo(CaseStatus.SUBMITTED);
        assertThat(submitted.seal().hashValid()).isTrue();
        assertThat(submitted.draft()).isFalse();
        assertThatThrownBy(complete::draftSubmission).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> complete.updateEvidence("PPF-01", SelfAssessment.NOK, null, "x", SQM, NOW))
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void submittedRevisionIsImmutable() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        Submission s = c.submit(true, VALIDATOR, (ca, x) -> CaseFixtures.seal("{}", SignatureStatus.VALID), SQM, NOW);
        assertThatThrownBy(() ->
                        s.replace(ElementEvidence.empty(c.request().elements().getFirst())))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.seal(NOW, "x", CaseFixtures.seal("{}", SignatureStatus.VALID)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detachDocument() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        UUID id = c.draftSubmission()
                .evidence("PPF-01")
                .orElseThrow()
                .documents()
                .getFirst()
                .documentId();
        c.detachEvidenceDocument("PPF-01", id, SQM, NOW);
        assertThat(c.draftSubmission().evidence("PPF-01").orElseThrow().documents())
                .isEmpty();
        assertThatThrownBy(() -> c.detachEvidenceDocument("PPF-01", id, SQM, NOW))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void customerReceivesSubmissionIdempotently() {
        ApprovalCase c = CaseFixtures.submittedCustomerCase();
        assertThat(c.status()).isEqualTo(CaseStatus.SUBMITTED);
        assertThat(c.receiveSubmission(
                        1,
                        List.of(),
                        NOW,
                        "x",
                        CaseFixtures.seal("{\"r\":1}", SignatureStatus.VALID),
                        Actor.exchange(),
                        NOW))
                .isFalse();
        assertThatThrownBy(() -> c.receiveSubmission(
                        1,
                        List.of(),
                        NOW,
                        "x",
                        CaseFixtures.seal("{\"r\":2}", SignatureStatus.VALID),
                        Actor.exchange(),
                        NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REVISION_CONFLICT);

        ApprovalCase other = CaseFixtures.draft();
        other.send(true, CQE, NOW, TODAY);
        assertThatThrownBy(() -> other.receiveSubmission(
                        2, List.of(), NOW, "x", CaseFixtures.seal("{}", SignatureStatus.VALID), Actor.exchange(), NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REVISION_CONFLICT);
        assertThatThrownBy(() -> other.receiveSubmission(
                        1, List.of(), NOW, "x", CaseFixtures.seal("{}", SignatureStatus.VALID), CQE, NOW))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void startReviewVerifiesHashAndSignature() {
        ApprovalCase invalid = CaseFixtures.submittedCustomerCase();
        assertThatThrownBy(() -> invalid.startReview(seal -> SignatureStatus.INVALID, CQE, NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.SIGNATURE_INVALID);

        ApprovalCase corrupt = CaseFixtures.draft();
        corrupt.send(true, CQE, NOW, TODAY);
        corrupt.receiveSubmission(
                1,
                List.of(),
                NOW,
                "x",
                new Seal("{}", "sha256:" + "0".repeat(64), SignatureInfo.NOT_SIGNED),
                Actor.exchange(),
                NOW);
        assertThatThrownBy(() -> corrupt.startReview(seal -> SignatureStatus.VALID, CQE, NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.PAYLOAD_HASH_MISMATCH);

        ApprovalCase c = CaseFixtures.submittedCustomerCase();
        assertThatThrownBy(() -> c.startReview(seal -> SignatureStatus.VALID, SI, NOW))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        c.startReview(seal -> SignatureStatus.UNVERIFIED, CQE, NOW);
        assertThat(c.status()).isEqualTo(CaseStatus.IN_REVIEW);
        assertThat(c.currentSubmission().orElseThrow().seal().signature().status())
                .isEqualTo(SignatureStatus.UNVERIFIED);
    }

    @Test
    void decisionRequiresJustifications() {
        ApprovalCase c = CaseFixtures.inReviewCase();
        assertThatThrownBy(() -> decide(c, DecisionResult.REJECTED, List.of(), null, CasePolicy.DEFAULT, CQE))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REJECTION_REASON_REQUIRED);
        c.recordFinding(new ElementFinding("PPF-17", FindingResult.NOT_ACCEPTED, null), CasePolicy.DEFAULT, CQE, NOW);
        assertThatThrownBy(() -> decide(c, DecisionResult.REJECTED, List.of(), null, CasePolicy.DEFAULT, CQE))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REJECTION_REASON_REQUIRED);
        assertThatThrownBy(() -> decide(c, DecisionResult.APPROVED, List.of(), null, CasePolicy.DEFAULT, CQE))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.ELEMENTS_NOT_ACCEPTED);
        assertThatThrownBy(() ->
                        decide(c, DecisionResult.CONDITIONALLY_APPROVED, List.of(" "), TODAY, CasePolicy.DEFAULT, CQE))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONDITIONS_REQUIRED);
        assertThatThrownBy(() -> c.recordFinding(
                        new ElementFinding("PPF-99", FindingResult.ACCEPTED, null), CasePolicy.DEFAULT, CQE, NOW))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> decide(c, DecisionResult.APPROVED, List.of(), null, CasePolicy.DEFAULT, SI))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        c.recordFinding(
                new ElementFinding("PPF-17", FindingResult.NOT_ACCEPTED, "quantity too low"),
                CasePolicy.DEFAULT,
                CQE,
                NOW);
        assertThatThrownBy(() -> c.decide(
                        DecisionResult.REJECTED,
                        List.of(),
                        null,
                        null,
                        false,
                        CaseFixtures.decisionSealer(),
                        CasePolicy.DEFAULT,
                        CQE,
                        NOW,
                        TODAY))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.CONFIRMATION_REQUIRED);
        Decision decision = decide(c, DecisionResult.REJECTED, List.of("ignored"), TODAY, CasePolicy.DEFAULT, CQE);
        assertThat(decision.conditions()).isEmpty();
        assertThat(decision.conditionDueDate()).isNull();
        assertThat(decision.elementFindings()).hasSize(1);
        assertThat(c.status()).isEqualTo(CaseStatus.REJECTED);
        assertThat(c.lastDecision()).contains(decision);
        assertThatThrownBy(() -> decide(c, DecisionResult.APPROVED, List.of(), null, CasePolicy.DEFAULT, CQE))
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void technicalClientsDecideOnlyWithPolicy() {
        ApprovalCase c = CaseFixtures.inReviewCase();
        var policy = new CasePolicy(true);
        c.recordFinding(new ElementFinding("PPF-01", FindingResult.ACCEPTED_WITH_CONDITION, null), policy, SI, NOW);
        Decision d = decide(
                c, DecisionResult.CONDITIONALLY_APPROVED, List.of("PPAP rev. B"), TODAY.plusDays(30), policy, SI);
        assertThat(d.conditions()).containsExactly("PPAP rev. B");
        assertThat(d.decidedBy()).isEqualTo("client:erp");
        assertThat(c.status()).isEqualTo(CaseStatus.CONDITIONALLY_APPROVED);
    }

    @Test
    void supplierReceivesDecisionAndStartsNewRevision() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        Submission s =
                c.submit(true, VALIDATOR, (ca, x) -> CaseFixtures.seal("{\"s\":1}", SignatureStatus.VALID), SQM, NOW);
        var rejected = new Decision(
                1,
                DecisionResult.REJECTED,
                List.of(),
                null,
                "no",
                List.of(new ElementFinding("PPF-17", FindingResult.NOT_ACCEPTED, "too few")),
                NOW,
                "oem.quality",
                CaseFixtures.seal("{\"d\":1}", SignatureStatus.UNVERIFIED));
        assertThatThrownBy(() -> c.receiveDecision(rejected, "sha256:" + "1".repeat(64), Actor.exchange(), NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.PAYLOAD_HASH_MISMATCH);
        assertThat(c.receiveDecision(rejected, s.seal().payloadHash(), Actor.exchange(), NOW))
                .isTrue();
        assertThat(c.status()).isEqualTo(CaseStatus.REJECTED);
        assertThat(c.receiveDecision(rejected, s.seal().payloadHash(), Actor.exchange(), NOW))
                .isFalse();
        var other = new Decision(
                1,
                DecisionResult.APPROVED,
                List.of(),
                null,
                null,
                List.of(),
                NOW,
                "x",
                CaseFixtures.seal("{\"d\":2}", SignatureStatus.VALID));
        assertThatThrownBy(() -> c.receiveDecision(other, s.seal().payloadHash(), Actor.exchange(), NOW))
                .hasFieldOrPropertyWithValue("code", ApprovalErrors.REVISION_CONFLICT);
        var unknown = new Decision(5, DecisionResult.APPROVED, List.of(), null, null, List.of(), NOW, "x", null);
        assertThatThrownBy(() -> c.receiveDecision(unknown, "h", Actor.exchange(), NOW))
                .isInstanceOf(NotFoundException.class);

        Submission next = c.newRevision(SQM, NOW);
        assertThat(next.revision()).isEqualTo(2);
        assertThat(next.basedOnRevision()).isEqualTo(1);
        assertThat(next.evidence("PPF-17").orElseThrow().values()).isEqualTo(Map.of("quantity", 5));
        assertThat(c.status()).isEqualTo(CaseStatus.IN_PROGRESS);
        assertThat(c.submissions()).hasSize(2);
        assertThat(c.submission(1).decision()).isPresent();
        assertThatThrownBy(() -> c.submission(7)).isInstanceOf(NotFoundException.class);
        assertThat(c.timeline())
                .extracting(TimelineEntry::action)
                .containsExactly(
                        CaseAction.CAPTURE_MANUAL,
                        CaseAction.START_WORK,
                        CaseAction.SUBMIT,
                        CaseAction.RECEIVE_DECISION,
                        CaseAction.NEW_REVISION);
    }

    @Test
    void overdueAndVersion() {
        ApprovalCase c = CaseFixtures.manual();
        assertThat(c.overdue(TODAY)).isFalse();
        assertThat(c.overdue(TODAY.plusDays(11))).isTrue();
        assertThat(c.version()).isNull();
        c.checkVersion(3L);
        ApprovalCase restored = ApprovalCase.restore(
                c.id(),
                c.caseNumber(),
                c.exchangeId(),
                c.ownRole(),
                c.source(),
                CaseStatus.APPROVED,
                c.header(),
                c.request(),
                c.submissions(),
                c.reviewFindings(),
                c.timeline(),
                new CaseMetadata(4, NOW, "a", NOW, "b"));
        assertThat(restored.overdue(TODAY.plusDays(30))).isFalse();
        restored.checkVersion(4L);
        restored.checkVersion(null);
        assertThatThrownBy(() -> restored.checkVersion(3L)).isInstanceOf(VersionConflictException.class);
        assertThat(restored.allowedActions(SQM, CasePolicy.DEFAULT)).isEmpty();
        assertThat(restored.metadata().createdBy()).isEqualTo("a");
    }

    @Test
    void valueObjectsNormalize() {
        assertThat(new ElementSnapshot(null, null, EvidenceType.VALUES, Map.of()).valueSchema())
                .isNull();
        assertThat(new CatalogRef("X", null, null).nameEn()).isEqualTo("X");
        assertThat(new PartnerRef("BPNL1", null).name()).isEqualTo("BPNL1");
        assertThat(new ElementFinding("A", FindingResult.ACCEPTED, " ").comment())
                .isNull();
        assertThat(CaseRole.CUSTOMER.partnerRole()).isEqualTo(CaseRole.SUPPLIER);
        assertThat(DecisionResult.CONDITIONALLY_APPROVED.status()).isEqualTo(CaseStatus.CONDITIONALLY_APPROVED);
        assertThat(EvidenceType.DOCUMENT.needsValues()).isFalse();
        assertThat(Actor.exchange().has(UserRole.EXCHANGE)).isTrue();
        assertThat(new CaseChange(
                                CaseAction.SEND,
                                CaseStatus.DRAFT,
                                CaseStatus.REQUESTED,
                                Instant.EPOCH,
                                "a",
                                null,
                                null,
                                null)
                        .transition())
                .isTrue();
    }

    private static Decision decide(
            ApprovalCase c,
            DecisionResult result,
            List<String> conditions,
            java.time.LocalDate due,
            CasePolicy policy,
            Actor actor) {
        return c.decide(
                result, conditions, due, "comment", true, CaseFixtures.decisionSealer(), policy, actor, NOW, TODAY);
    }
}
