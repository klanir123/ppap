package com.msg.ppap.approval.domain;

import static com.msg.ppap.approval.domain.CaseFixtures.CQE;
import static com.msg.ppap.approval.domain.CaseFixtures.NOW;
import static com.msg.ppap.approval.domain.CaseFixtures.SI;
import static com.msg.ppap.approval.domain.CaseFixtures.SQM;
import static com.msg.ppap.approval.domain.CaseFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cascade rules (UC-09), sub-supplier references, signature re-verification and actor display (phase 6). */
class CascadeDomainTest {

    static final Actor TIER1 =
            Actor.user("tier1.quality", UserRole.SUPPLIER_QUALITY_MANAGER, UserRole.CUSTOMER_QUALITY_ENGINEER);

    static SubSupplierReference reference(UUID childId, DecisionResult result) {
        return new SubSupplierReference(
                childId,
                "BPNL000000TIER02",
                "Tier2",
                UUID.randomUUID(),
                "PPF-2026-00099",
                "T1-2000",
                "Rastnase",
                "T2-88",
                1,
                result,
                Instant.parse("2026-11-18T10:00:00Z"),
                "sha256:" + "c".repeat(64),
                null);
    }

    @Test
    void subRequestFromSupplierCaseLinksParentAndRecordsTheAction() {
        ApprovalCase parent = CaseFixtures.manual();
        parent.pullChanges();
        ApprovalCase child = ApprovalCase.createSubRequest(
                parent, "PPF-2026-00010", CaseFixtures.header(TODAY.plusDays(5)), CaseFixtures.elements(), TIER1, NOW);
        assertThat(child.parentCaseId()).isEqualTo(parent.id());
        assertThat(child.ownRole()).isEqualTo(CaseRole.CUSTOMER);
        assertThat(child.status()).isEqualTo(CaseStatus.DRAFT);
        assertThat(parent.pullChanges()).singleElement().satisfies(change -> {
            assertThat(change.action()).isEqualTo(CaseAction.REQUEST_SUB_SUPPLIER);
            assertThat(change.transition()).isFalse();
            assertThat(change.comment()).isEqualTo("PPF-2026-00010");
        });
        assertThat(parent.timeline()).hasSize(1);
        // allowed in IN_PROGRESS too
        parent.startWork(SQM, NOW);
        assertThat(ApprovalCase.createSubRequest(
                                parent,
                                "PPF-2026-00011",
                                CaseFixtures.header(TODAY.plusDays(5)),
                                CaseFixtures.elements(),
                                SI,
                                NOW)
                        .parentCaseId())
                .isEqualTo(parent.id());
    }

    @Test
    void subRequestNeedsBothRolesASupplierParentAndAnOpenStatus() {
        ApprovalCase parent = CaseFixtures.manual();
        assertThatThrownBy(() -> ApprovalCase.createSubRequest(
                        parent, "X", CaseFixtures.header(TODAY), CaseFixtures.elements(), SQM, NOW))
                .isInstanceOf(PpapException.class)
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo(ApprovalErrors.FORBIDDEN);
        assertThatThrownBy(() -> ApprovalCase.createSubRequest(
                        parent, "X", CaseFixtures.header(TODAY), CaseFixtures.elements(), CQE, NOW))
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo(ApprovalErrors.FORBIDDEN);
        ApprovalCase customerCase = CaseFixtures.draft();
        assertThatThrownBy(() -> ApprovalCase.createSubRequest(
                        customerCase, "X", CaseFixtures.header(TODAY), CaseFixtures.elements(), TIER1, NOW))
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo(ApprovalErrors.WRONG_CASE_ROLE);
        ApprovalCase submitted = CaseFixtures.completeSupplierCase();
        submitted.submit(
                true, CaseFixtures.VALIDATOR, (c, s) -> CaseFixtures.seal("{}", SignatureStatus.VALID), SQM, NOW);
        assertThatThrownBy(() -> ApprovalCase.createSubRequest(
                        submitted, "X", CaseFixtures.header(TODAY), CaseFixtures.elements(), TIER1, NOW))
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo(ApprovalErrors.INVALID_STATE_TRANSITION);
    }

    @Test
    void allowedActionsOfferTheSubRequestOnlyWithBothRoles() {
        ApprovalCase parent = CaseFixtures.manual();
        assertThat(parent.allowedActions(TIER1, CasePolicy.DEFAULT)).contains(CaseAction.REQUEST_SUB_SUPPLIER);
        assertThat(parent.allowedActions(SI, CasePolicy.DEFAULT)).contains(CaseAction.REQUEST_SUB_SUPPLIER);
        assertThat(parent.allowedActions(SQM, CasePolicy.DEFAULT)).doesNotContain(CaseAction.REQUEST_SUB_SUPPLIER);
    }

    @Test
    void referencesAreEditableInTheDraftOnlyAndPartOfTheSubmittedRevision() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        UUID child = UUID.randomUUID();
        SubSupplierReference ref = reference(child, DecisionResult.APPROVED);
        c.replaceSubSupplierReferences(List.of(ref, ref), SQM, NOW);
        assertThat(c.draftSubmission().subSupplierReferences()).containsExactly(ref);
        List<SubSupplierReference> sealed = new java.util.ArrayList<>();
        Submission submitted = c.submit(
                true,
                CaseFixtures.VALIDATOR,
                (ac, s) -> {
                    sealed.addAll(s.subSupplierReferences());
                    return CaseFixtures.seal("{}", SignatureStatus.VALID);
                },
                SQM,
                NOW);
        assertThat(sealed).containsExactly(ref);
        assertThat(submitted.subSupplierReferences()).containsExactly(ref);
        assertThatThrownBy(() -> c.replaceSubSupplierReferences(List.of(), SQM, NOW))
                .isInstanceOf(PpapException.class);
    }

    @Test
    void sealedViewCarriesTheReferences() {
        ApprovalCase c = CaseFixtures.completeSupplierCase();
        SubSupplierReference ref = reference(UUID.randomUUID(), DecisionResult.CONDITIONALLY_APPROVED);
        c.replaceSubSupplierReferences(List.of(ref), SQM, NOW);
        Submission draft = c.draftSubmission();
        Submission next = draft.asSubmitted(NOW, "x");
        assertThat(next.subSupplierReferences()).containsExactly(ref);
    }

    @Test
    void rejectedDecisionsCannotBeReferenced() {
        assertThatThrownBy(() -> reference(UUID.randomUUID(), DecisionResult.REJECTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SubSupplierReference.referenceable(DecisionResult.APPROVED)).isTrue();
        assertThat(SubSupplierReference.referenceable(DecisionResult.REJECTED)).isFalse();
    }

    @Test
    void receivedReferencesLoseTheLocalChildCaseId() {
        ApprovalCase c = CaseFixtures.draft();
        c.send(true, CQE, NOW, TODAY);
        c.receiveSubmission(
                1,
                List.of(),
                List.of(reference(UUID.randomUUID(), DecisionResult.APPROVED)),
                NOW,
                "tier1.quality",
                CaseFixtures.seal("{\"r\":1}", SignatureStatus.VALID),
                ExchangeMode.PUSH,
                Actor.exchange(),
                NOW);
        assertThat(c.submission(1).subSupplierReferences())
                .singleElement()
                .satisfies(r -> assertThat(r.childCaseId()).isNull());
    }

    @Test
    void signatureCanBeVerifiedAgain() {
        ApprovalCase c = CaseFixtures.draft();
        c.send(true, CQE, NOW, TODAY);
        c.receiveSubmission(
                1,
                List.of(),
                NOW,
                "x",
                CaseFixtures.seal("{\"r\":1}", SignatureStatus.UNVERIFIED),
                Actor.exchange(),
                NOW);
        c.pullChanges();
        assertThat(c.reverifySignature(1, seal -> SignatureStatus.UNVERIFIED, Actor.exchange(), NOW))
                .isEqualTo(SignatureStatus.UNVERIFIED);
        assertThat(c.pullChanges()).isEmpty();
        assertThat(c.reverifySignature(1, seal -> SignatureStatus.VALID, Actor.exchange(), NOW))
                .isEqualTo(SignatureStatus.VALID);
        assertThat(c.submission(1).seal().signature().status()).isEqualTo(SignatureStatus.VALID);
        assertThat(c.pullChanges())
                .singleElement()
                .satisfies(ch -> assertThat(ch.action()).isEqualTo(CaseAction.VERIFY_SIGNATURE));
        assertThatThrownBy(() -> c.reverifySignature(2, seal -> SignatureStatus.VALID, Actor.exchange(), NOW))
                .isInstanceOf(NotFoundException.class);
        ApprovalCase corrupt = CaseFixtures.draft();
        corrupt.send(true, CQE, NOW, TODAY);
        corrupt.receiveSubmission(
                1,
                List.of(),
                NOW,
                "x",
                new Seal(
                        "{}",
                        "sha256:" + "0".repeat(64),
                        new SignatureInfo("ES256", "k", "j", SignatureStatus.UNVERIFIED)),
                Actor.exchange(),
                NOW);
        assertThat(corrupt.reverifySignature(1, seal -> SignatureStatus.VALID, Actor.exchange(), NOW))
                .isEqualTo(SignatureStatus.INVALID);
    }

    @Test
    void hintsNeverMakeARevisionIncomplete() {
        Completeness complete = new Completeness(1, 1, Map.of());
        Completeness withHint = complete.withHints(
                List.of(new Completeness.Issue(null, Completeness.SUB_SUPPLIER_PENDING, "pending", null)));
        assertThat(withHint.complete()).isTrue();
        assertThat(withHint.hints()).extracting(Completeness.Issue::code).containsExactly("SUB_SUPPLIER_PENDING");
        assertThat(complete.withHints(List.of())).isSameAs(complete);
    }

    @Test
    void actorDisplayShowsPartnerNames() {
        assertThat(Actor.display("partner:BPNL000000TIER02", b -> Optional.of("Demo-Tier2 KG")))
                .isEqualTo("Demo-Tier2 KG");
        assertThat(Actor.display("partner:BPNL000000TIER02", b -> Optional.empty()))
                .isEqualTo("BPNL000000TIER02");
        assertThat(Actor.display("partner:BPNL000000TIER02", b -> Optional.of(" ")))
                .isEqualTo("BPNL000000TIER02");
        assertThat(Actor.display("oem.quality", b -> Optional.of("x"))).isEqualTo("oem.quality");
        assertThat(Actor.display(null, b -> Optional.empty())).isNull();
    }

    @Test
    void conditionalReferenceKeepsDueDate() {
        var ref = new SubSupplierReference(
                null,
                "BPNL000000TIER02",
                " ",
                UUID.randomUUID(),
                "PPF-1",
                "T1",
                "",
                null,
                2,
                DecisionResult.CONDITIONALLY_APPROVED,
                NOW,
                "sha256:" + "d".repeat(64),
                LocalDate.parse("2026-12-31"));
        assertThat(ref.subSupplierName()).isNull();
        assertThat(ref.partName()).isNull();
        assertThat(ref.withoutChildCase().conditionDueDate()).isEqualTo(LocalDate.parse("2026-12-31"));
    }
}
