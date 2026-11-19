package com.msg.ppap.approval.domain;

import static com.msg.ppap.approval.domain.CaseFixtures.CQE;
import static com.msg.ppap.approval.domain.CaseFixtures.NOW;
import static com.msg.ppap.approval.domain.CaseFixtures.SI;
import static com.msg.ppap.approval.domain.CaseFixtures.SQM;
import static com.msg.ppap.approval.domain.CaseFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.PpapException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Remote transitions RECEIVE_* (actor partner:<BPNL>) and the clarification list (UC-04 A1). */
class RemoteTransitionsTest {

    static final Actor PARTNER = Actor.partner("BPNL00000003AYRE");

    static ApprovalCase received(Clarification clarification) {
        CaseHeader h = CaseFixtures.header(TODAY.plusDays(10));
        CaseHeader header = clarification == null
                ? h
                : new CaseHeader(
                        h.partner(),
                        h.relationId(),
                        PartRef.unmapped("Halter"),
                        "OEM-4711",
                        h.reason(),
                        h.level(),
                        h.dueDate(),
                        null,
                        null);
        return ApprovalCase.receiveRequest(
                "PPF-2026-00100",
                UUID.randomUUID(),
                header,
                new ApprovalRequest(NOW, "oem.quality", CaseFixtures.elements(), List.of()),
                clarification,
                PARTNER,
                NOW);
    }

    @Test
    void partnerActorIsTechnicalExchangeActor() {
        assertThat(PARTNER.name()).isEqualTo("partner:BPNL00000003AYRE");
        assertThat(PARTNER.has(UserRole.EXCHANGE)).isTrue();
        ApprovalCase c = received(null);
        assertThat(c.timeline().getFirst().actor()).isEqualTo("partner:BPNL00000003AYRE");
        assertThat(c.source()).isEqualTo(CaseSource.EXCHANGE);
    }

    @Test
    void withdrawalCancelsIdempotently() {
        ApprovalCase c = received(null);
        assertThat(c.receiveWithdrawal("Teil entfällt", PARTNER, NOW)).isTrue();
        assertThat(c.status()).isEqualTo(CaseStatus.CANCELLED);
        assertThat(c.timeline().getLast().comment()).isEqualTo("Teil entfällt");
        assertThat(c.receiveWithdrawal("again", PARTNER, NOW)).isFalse();
        assertThat(c.pullChanges()).extracting(CaseChange::action).contains(CaseAction.RECEIVE_WITHDRAWAL);
    }

    @Test
    void withdrawalOnlyByExchangeAndBeforeSubmission() {
        ApprovalCase c = received(null);
        assertThatThrownBy(() -> c.receiveWithdrawal("x", SQM, NOW)).isInstanceOf(PpapException.class);
        c.startWork(SQM, NOW);
        assertThat(c.receiveWithdrawal("late", PARTNER, NOW)).isTrue();

        ApprovalCase submitted = CaseFixtures.completeSupplierCase();
        submitted.submit(
                true, CaseFixtures.VALIDATOR, (cc, sub) -> CaseFixtures.seal("{}", SignatureStatus.VALID), SQM, NOW);
        assertThatThrownBy(() -> submitted.receiveWithdrawal("x", PARTNER, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void customerFollowsSupplierToInProgressAndIgnoresStaleUpdates() {
        ApprovalCase c = CaseFixtures.draft();
        c.send(true, CQE, NOW, TODAY);
        assertThat(c.receiveStatusUpdate(CaseStatus.IN_PROGRESS, 1, PARTNER, NOW))
                .isTrue();
        assertThat(c.status()).isEqualTo(CaseStatus.IN_PROGRESS);
        assertThat(c.receiveStatusUpdate(CaseStatus.IN_PROGRESS, 1, PARTNER, NOW))
                .isFalse();

        // submission overtook the status update of the same revision
        ApprovalCase submitted = CaseFixtures.submittedCustomerCase();
        assertThat(submitted.receiveStatusUpdate(CaseStatus.IN_PROGRESS, 1, PARTNER, NOW))
                .isFalse();
        assertThat(submitted.status()).isEqualTo(CaseStatus.SUBMITTED);
        assertThatThrownBy(() -> submitted.receiveStatusUpdate(CaseStatus.IN_REVIEW, 1, PARTNER, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void customerFollowsNewRevisionAfterRejection() {
        ApprovalCase c = CaseFixtures.inReviewCase();
        c.recordFinding(
                new ElementFinding("PPF-01", FindingResult.NOT_ACCEPTED, "fehlt"), CasePolicy.DEFAULT, CQE, NOW);
        c.decide(
                DecisionResult.REJECTED,
                null,
                null,
                "nein",
                true,
                CaseFixtures.decisionSealer(),
                CasePolicy.DEFAULT,
                CQE,
                NOW,
                TODAY);
        assertThat(c.receiveStatusUpdate(CaseStatus.IN_PROGRESS, 2, PARTNER, NOW))
                .isTrue();
        assertThat(c.status()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void supplierFollowsCustomerToReview() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        c.submit(true, CaseFixtures.VALIDATOR, (cc, s) -> CaseFixtures.seal("{}", SignatureStatus.VALID), SQM, NOW);
        assertThatThrownBy(() -> c.receiveStatusUpdate(CaseStatus.IN_REVIEW, 2, PARTNER, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "REVISION_CONFLICT");
        // manual cases have no partner messages, but the state machine accepts the transition for the supplier role
        assertThat(c.receiveStatusUpdate(CaseStatus.IN_REVIEW, 1, PARTNER, NOW)).isTrue();
        assertThat(c.status()).isEqualTo(CaseStatus.IN_REVIEW);
        assertThat(c.receiveStatusUpdate(CaseStatus.IN_REVIEW, 1, PARTNER, NOW)).isFalse();
        assertThatThrownBy(() -> c.receiveStatusUpdate(CaseStatus.IN_PROGRESS, 1, PARTNER, NOW))
                .isInstanceOf(PpapException.class);
    }

    @Test
    void unmappedPartGoesToClarificationAndBlocksWork() {
        ApprovalCase c = received(Clarification.partNotMapped("OEM-4711"));
        assertThat(c.clarification().reason()).isEqualTo(Clarification.Reason.PART_NOT_MAPPED);
        assertThat(c.header().part().mapped()).isFalse();
        assertThat(c.allowedActions(SQM, CasePolicy.DEFAULT))
                .contains(CaseAction.ASSIGN_PART)
                .doesNotContain(CaseAction.START_WORK);
        assertThatThrownBy(() -> c.startWork(SQM, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "CLARIFICATION_PENDING");

        c.assignPart(new PartRef(UUID.randomUUID(), "T1-4711", "Halter", "Bracket"), SI, NOW);
        assertThat(c.clarification()).isNull();
        assertThat(c.supplierPartNumber()).isEqualTo("T1-4711");
        assertThat(c.allowedActions(SQM, CasePolicy.DEFAULT))
                .contains(CaseAction.START_WORK)
                .doesNotContain(CaseAction.ASSIGN_PART);
        assertThatThrownBy(() -> c.assignPart(new PartRef(UUID.randomUUID(), "X", null, null), SQM, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "NO_CLARIFICATION_PENDING");
        c.startWork(SQM, NOW);
        assertThat(c.status()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void restoreKeepsClarificationAndPartRefRules() {
        ApprovalCase c = received(Clarification.partNotMapped("OEM-1"));
        ApprovalCase restored = ApprovalCase.restore(
                c.id(),
                c.caseNumber(),
                c.exchangeId(),
                c.ownRole(),
                c.source(),
                c.status(),
                c.header(),
                c.request(),
                c.submissions(),
                c.reviewFindings(),
                c.timeline(),
                null,
                c.clarification());
        assertThat(restored.clarification()).isEqualTo(c.clarification());
        assertThatThrownBy(() -> new PartRef(UUID.randomUUID(), null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void startReviewByTechnicalClientOnlyWithPolicy() {
        ApprovalCase c = CaseFixtures.submittedCustomerCase();
        assertThatThrownBy(() -> c.startReview(seal -> SignatureStatus.VALID, SI, NOW))
                .isInstanceOf(PpapException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        c.startReview(seal -> SignatureStatus.VALID, new CasePolicy(true), SI, NOW);
        assertThat(c.status()).isEqualTo(CaseStatus.IN_REVIEW);
    }
}
