package com.msg.ppap.approval.domain;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.VersionConflictException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * Approval case of one side (customer or supplier) – aggregate root of the request, the submission revisions with
 * element evidence, the review findings and the decisions. Both sides' cases are linked by the {@code exchangeId}.
 *
 * <p>Every operation is checked against {@link CaseStateMachine} (case role, user role, status) and its guards;
 * applied actions are collected as {@link CaseChange}s (published as domain events by the application layer) and
 * status changes are appended to the timeline. Submitted revisions and decisions are immutable.
 */
@AggregateRoot
public final class ApprovalCase {

    /** Object type in audit events. */
    public static final String OBJECT_TYPE = "APPROVAL_CASE";

    /** Maximum length of a cancellation reason. */
    public static final int CANCEL_REASON_MAX = 500;

    @Identity
    private final UUID id;

    private final String caseNumber;
    private final UUID exchangeId;
    private final CaseRole ownRole;
    private final CaseSource source;
    private CaseStatus status;
    private CaseHeader header;
    private ApprovalRequest request;
    private final List<Submission> submissions;
    private final Map<String, ElementFinding> reviewFindings;
    private final List<TimelineEntry> timeline;
    private final CaseMetadata metadata;
    private Clarification clarification;
    private final List<CaseChange> changes = new ArrayList<>();

    private ApprovalCase(
            UUID id,
            String caseNumber,
            UUID exchangeId,
            CaseRole ownRole,
            CaseSource source,
            CaseStatus status,
            CaseHeader header,
            ApprovalRequest request,
            List<Submission> submissions,
            Collection<ElementFinding> reviewFindings,
            List<TimelineEntry> timeline,
            CaseMetadata metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.caseNumber = Objects.requireNonNull(caseNumber, "caseNumber");
        this.exchangeId = Objects.requireNonNull(exchangeId, "exchangeId");
        this.ownRole = Objects.requireNonNull(ownRole, "ownRole");
        this.source = Objects.requireNonNull(source, "source");
        this.status = Objects.requireNonNull(status, "status");
        this.header = Objects.requireNonNull(header, "header");
        this.request = Objects.requireNonNull(request, "request");
        this.submissions = new ArrayList<>(submissions);
        this.submissions.sort(Comparator.comparingInt(Submission::revision));
        this.reviewFindings = new LinkedHashMap<>();
        reviewFindings.forEach(f -> this.reviewFindings.put(f.code(), f));
        this.timeline = new ArrayList<>(timeline);
        this.metadata = metadata;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------------------------------------------------

    /** Customer creates a draft request (UC-03). */
    public static ApprovalCase createDraft(
            String caseNumber, CaseHeader header, List<RequestedElement> elements, Actor actor, Instant now) {
        CaseStateMachine.checkCreate(CaseAction.CREATE, CaseRole.CUSTOMER, CaseSource.LOCAL, actor);
        var created = new ApprovalCase(
                Ids.newId(),
                caseNumber,
                UUID.randomUUID(),
                CaseRole.CUSTOMER,
                CaseSource.LOCAL,
                CaseStatus.DRAFT,
                header,
                new ApprovalRequest(null, null, elements, List.of()),
                List.of(),
                List.of(),
                List.of(),
                null);
        created.record(CaseAction.CREATE, null, CaseStatus.DRAFT, actor, now, null, null, null);
        return created;
    }

    /** Supplier captures a request received outside the dataspace (source {@code MANUAL}). */
    public static ApprovalCase captureManual(
            String caseNumber, CaseHeader header, List<RequestedElement> elements, Actor actor, Instant now) {
        CaseStateMachine.checkCreate(CaseAction.CAPTURE_MANUAL, CaseRole.SUPPLIER, CaseSource.MANUAL, actor);
        requireElements(elements);
        var created = new ApprovalCase(
                Ids.newId(),
                caseNumber,
                UUID.randomUUID(),
                CaseRole.SUPPLIER,
                CaseSource.MANUAL,
                CaseStatus.REQUESTED,
                header,
                new ApprovalRequest(now, actor.name(), elements, List.of()),
                List.of(),
                List.of(),
                List.of(),
                null);
        created.record(CaseAction.CAPTURE_MANUAL, null, CaseStatus.REQUESTED, actor, now, null, null, null);
        return created;
    }

    /** Supplier receives a request via the dataspace (source {@code EXCHANGE}). */
    public static ApprovalCase receiveRequest(
            String caseNumber, UUID exchangeId, CaseHeader header, ApprovalRequest request, Actor actor, Instant now) {
        return receiveRequest(caseNumber, exchangeId, header, request, null, actor, now);
    }

    /**
     * Supplier receives a request via the dataspace (source {@code EXCHANGE}); with a {@link Clarification} if the
     * request cannot be processed as is (e.g. part not mapped, UC-04 A1).
     */
    public static ApprovalCase receiveRequest(
            String caseNumber,
            UUID exchangeId,
            CaseHeader header,
            ApprovalRequest request,
            Clarification clarification,
            Actor actor,
            Instant now) {
        CaseStateMachine.checkCreate(CaseAction.RECEIVE_REQUEST, CaseRole.SUPPLIER, CaseSource.EXCHANGE, actor);
        requireElements(request.elements());
        var created = new ApprovalCase(
                Ids.newId(),
                caseNumber,
                exchangeId,
                CaseRole.SUPPLIER,
                CaseSource.EXCHANGE,
                CaseStatus.REQUESTED,
                header,
                request,
                List.of(),
                List.of(),
                List.of(),
                null);
        created.clarification = clarification;
        created.record(CaseAction.RECEIVE_REQUEST, null, CaseStatus.REQUESTED, actor, now, null, null, null);
        return created;
    }

    /** Restores a persisted case with an open clarification without checks. */
    public static ApprovalCase restore(
            UUID id,
            String caseNumber,
            UUID exchangeId,
            CaseRole ownRole,
            CaseSource source,
            CaseStatus status,
            CaseHeader header,
            ApprovalRequest request,
            List<Submission> submissions,
            Collection<ElementFinding> reviewFindings,
            List<TimelineEntry> timeline,
            CaseMetadata metadata,
            Clarification clarification) {
        ApprovalCase restored = restore(
                id,
                caseNumber,
                exchangeId,
                ownRole,
                source,
                status,
                header,
                request,
                submissions,
                reviewFindings,
                timeline,
                metadata);
        restored.clarification = clarification;
        return restored;
    }

    /** Restores a persisted (or seeded) case without checks. */
    public static ApprovalCase restore(
            UUID id,
            String caseNumber,
            UUID exchangeId,
            CaseRole ownRole,
            CaseSource source,
            CaseStatus status,
            CaseHeader header,
            ApprovalRequest request,
            List<Submission> submissions,
            Collection<ElementFinding> reviewFindings,
            List<TimelineEntry> timeline,
            CaseMetadata metadata) {
        return new ApprovalCase(
                id,
                caseNumber,
                exchangeId,
                ownRole,
                source,
                status,
                header,
                request,
                submissions,
                reviewFindings,
                timeline,
                metadata);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Request (customer)
    // ---------------------------------------------------------------------------------------------------------

    /** Changes the header of a draft. Partner, relation and part stay unchanged. */
    public void updateDraft(
            CatalogRef reason, CatalogRef level, LocalDate dueDate, String remarks, Actor actor, Instant now) {
        check(CaseAction.EDIT, actor, CasePolicy.DEFAULT);
        header = new CaseHeader(
                header.partner(),
                header.relationId(),
                header.part(),
                header.partnerPartNumber(),
                reason,
                level,
                dueDate,
                remarks,
                header.externalReference());
        record(CaseAction.EDIT, status, status, actor, now, null, null, null);
    }

    /** Replaces the requested elements of a draft. */
    public void replaceRequestElements(List<RequestedElement> elements, Actor actor, Instant now) {
        check(CaseAction.EDIT, actor, CasePolicy.DEFAULT);
        request = request.withElements(elements);
        record(CaseAction.EDIT, status, status, actor, now, null, null, null);
    }

    /** Adds an attachment to a draft request (idempotent). */
    public void addAttachment(DocumentRef document, Actor actor, Instant now) {
        check(CaseAction.EDIT, actor, CasePolicy.DEFAULT);
        if (request.attachments().stream().noneMatch(a -> Objects.equals(a.documentId(), document.documentId()))) {
            var attachments = new ArrayList<>(request.attachments());
            attachments.add(document);
            request = request.withAttachments(attachments);
            record(CaseAction.EDIT, status, status, actor, now, null, null, null);
        }
    }

    /** Removes an attachment from a draft request. */
    public void removeAttachment(UUID documentId, Actor actor, Instant now) {
        check(CaseAction.EDIT, actor, CasePolicy.DEFAULT);
        var attachments = new ArrayList<>(request.attachments());
        if (!attachments.removeIf(a -> documentId.equals(a.documentId()))) {
            throw new NotFoundException("Attachment", documentId);
        }
        request = request.withAttachments(attachments);
        record(CaseAction.EDIT, status, status, actor, now, null, null, null);
    }

    /**
     * Sends the request: DRAFT → REQUESTED. Guards: at least one element, due date in the future, partner ready for
     * the dataspace exchange.
     */
    public void send(boolean partnerExchangeReady, Actor actor, Instant now, LocalDate today) {
        check(CaseAction.SEND, actor, CasePolicy.DEFAULT);
        if (request.elements().isEmpty()) {
            throw ApprovalErrors.unprocessable(ApprovalErrors.NO_ELEMENTS, "The request has no elements");
        }
        if (!header.dueDate().isAfter(today)) {
            throw ApprovalErrors.rule(
                    ApprovalErrors.DUE_DATE_IN_PAST,
                    List.of(new FieldViolation(
                            "dueDate", ApprovalErrors.DUE_DATE_IN_PAST, "due date must be in the future")));
        }
        if (!partnerExchangeReady) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.PARTNER_NOT_EXCHANGE_READY,
                    "Partner " + header.partner().bpnl() + " has no DSP endpoint/DID or is inactive");
        }
        request = request.sent(now, actor.name());
        transition(CaseAction.SEND, CaseStatus.REQUESTED, actor, now, null, null);
    }

    /** Cancels a draft or request (customer; supplier only for manually captured requests). */
    public void cancel(String reason, Actor actor, Instant now) {
        check(CaseAction.CANCEL, actor, CasePolicy.DEFAULT);
        if (reason == null || reason.isBlank()) {
            throw ValidationException.of("reason", ViolationCodes.REQUIRED, "a reason is required");
        }
        if (reason.strip().length() > CANCEL_REASON_MAX) {
            throw ValidationException.of(
                    "reason", ViolationCodes.TOO_LONG, "must be at most " + CANCEL_REASON_MAX + " characters");
        }
        transition(CaseAction.CANCEL, CaseStatus.CANCELLED, actor, now, reason, null);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Submission (supplier)
    // ---------------------------------------------------------------------------------------------------------

    /** Starts the work: REQUESTED → IN_PROGRESS with draft revision 1. */
    public void startWork(Actor actor, Instant now) {
        check(CaseAction.START_WORK, actor, CasePolicy.DEFAULT);
        if (clarification != null) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.CLARIFICATION_PENDING,
                    "The request needs clarification first (" + clarification.reason() + ")");
        }
        int revision =
                submissions.isEmpty() ? 1 : currentSubmission().orElseThrow().revision() + 1;
        submissions.add(Submission.draft(
                revision,
                null,
                request.elements().stream().map(ElementEvidence::empty).toList()));
        transition(CaseAction.START_WORK, CaseStatus.IN_PROGRESS, actor, now, null, revision);
    }

    /**
     * Updates self assessment, values and comment of an element of the draft revision. Values are stored even if
     * they violate the element's value schema (a draft is work in progress); violations are reported by {@link
     * #completeness} ({@code VALUE_SCHEMA_VIOLATION} with JSON pointer) and block the submission.
     */
    public void updateEvidence(
            String code, SelfAssessment selfAssessment, Object values, String comment, Actor actor, Instant now) {
        check(CaseAction.EDIT_SUBMISSION, actor, CasePolicy.DEFAULT);
        RequestedElement element = requestedElement(code);
        Submission draft = draftSubmission();
        ElementEvidence current = draft.evidence(code).orElseGet(() -> ElementEvidence.empty(element));
        draft.replace(current.withContent(selfAssessment, values, comment));
        record(CaseAction.EDIT_SUBMISSION, status, status, actor, now, null, draft.revision(), code);
    }

    /** Attaches a document to an element of the draft revision (idempotent). */
    public void attachEvidenceDocument(String code, DocumentRef document, Actor actor, Instant now) {
        check(CaseAction.EDIT_SUBMISSION, actor, CasePolicy.DEFAULT);
        RequestedElement element = requestedElement(code);
        Submission draft = draftSubmission();
        ElementEvidence current = draft.evidence(code).orElseGet(() -> ElementEvidence.empty(element));
        if (current.documents().stream().noneMatch(d -> Objects.equals(d.documentId(), document.documentId()))) {
            var documents = new ArrayList<>(current.documents());
            documents.add(document);
            draft.replace(current.withDocuments(documents));
            record(CaseAction.EDIT_SUBMISSION, status, status, actor, now, null, draft.revision(), code);
        }
    }

    /** Detaches a document from an element of the draft revision. */
    public void detachEvidenceDocument(String code, UUID documentId, Actor actor, Instant now) {
        check(CaseAction.EDIT_SUBMISSION, actor, CasePolicy.DEFAULT);
        requestedElement(code);
        Submission draft = draftSubmission();
        ElementEvidence current = draft.evidence(code).orElseThrow(() -> new NotFoundException("Document", documentId));
        var documents = new ArrayList<>(current.documents());
        if (!documents.removeIf(d -> documentId.equals(d.documentId()))) {
            throw new NotFoundException("Document", documentId);
        }
        draft.replace(current.withDocuments(documents));
        record(CaseAction.EDIT_SUBMISSION, status, status, actor, now, null, draft.revision(), code);
    }

    /** Completeness of a revision against the request. */
    public Completeness completeness(Submission submission, ValueValidator validator) {
        return Completeness.evaluate(request.elements(), submission.evidenceByCode(), validator);
    }

    /** Builds the exchange payload of a revision, signs it and returns the seal. */
    @FunctionalInterface
    public interface SubmissionSealer {
        Seal seal(ApprovalCase approvalCase, Submission submission);
    }

    /**
     * Submits the draft revision: IN_PROGRESS → SUBMITTED. The revision must be complete ({@code
     * INCOMPLETE_SUBMISSION} with all issues); it is sealed (canonical payload, hash, signature) and immutable
     * afterwards.
     */
    public Submission submit(
            boolean confirmed, ValueValidator validator, SubmissionSealer sealer, Actor actor, Instant now) {
        return submit(confirmed, validator, sealer, null, actor, now);
    }

    /**
     * Submits the draft revision and records the exchange mode with which it is provided to the customer.
     *
     * @param exchangeMode effective mode of the relation, {@code null} for cases without dataspace exchange
     */
    public Submission submit(
            boolean confirmed,
            ValueValidator validator,
            SubmissionSealer sealer,
            ExchangeMode exchangeMode,
            Actor actor,
            Instant now) {
        check(CaseAction.SUBMIT, actor, CasePolicy.DEFAULT);
        requireConfirmation(confirmed);
        Submission draft = draftSubmission();
        Completeness completeness = completeness(draft, validator);
        if (!completeness.complete()) {
            throw ApprovalErrors.rule(
                    ApprovalErrors.INCOMPLETE_SUBMISSION,
                    completeness.issues().stream()
                            .map(issue -> new FieldViolation(
                                    "elements." + issue.elementCode()
                                            + (issue.pointer() == null ? "" : ".values" + issue.pointer()),
                                    issue.code(),
                                    issue.message()))
                            .toList());
        }
        Seal seal = sealer.seal(this, draft.asSubmitted(now, actor.name()));
        draft.seal(now, actor.name(), seal);
        draft.exchangeMode(exchangeMode);
        transition(CaseAction.SUBMIT, CaseStatus.SUBMITTED, actor, now, null, draft.revision());
        return draft;
    }

    /** New draft revision n+1 after rejection or conditional approval, prefilled from the last revision. */
    public Submission newRevision(Actor actor, Instant now) {
        check(CaseAction.NEW_REVISION, actor, CasePolicy.DEFAULT);
        Submission next = currentSubmission().orElseThrow().nextDraft(request.elements());
        submissions.add(next);
        transition(CaseAction.NEW_REVISION, CaseStatus.IN_PROGRESS, actor, now, null, next.revision());
        return next;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Inbound (customer receives submission, supplier receives decision)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Customer receives a submitted revision. Idempotent: the same revision with the same hash is ignored.
     *
     * @return {@code false} if the revision had already been received
     */
    public boolean receiveSubmission(
            int revision,
            List<ElementEvidence> elements,
            Instant submittedAt,
            String submittedBy,
            Seal seal,
            Actor actor,
            Instant now) {
        return receiveSubmission(revision, elements, submittedAt, submittedBy, seal, ExchangeMode.PUSH, actor, now);
    }

    /** Customer receives a submitted revision via the given exchange mode (push or pull). */
    @SuppressWarnings("java:S107")
    public boolean receiveSubmission(
            int revision,
            List<ElementEvidence> elements,
            Instant submittedAt,
            String submittedBy,
            Seal seal,
            ExchangeMode exchangeMode,
            Actor actor,
            Instant now) {
        Optional<Submission> existing =
                submissions.stream().filter(s -> s.revision() == revision).findFirst();
        if (existing.isPresent()) {
            if (existing.get().seal() != null
                    && existing.get().seal().payloadHash().equals(seal.payloadHash())) {
                return false;
            }
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.REVISION_CONFLICT,
                    "Revision " + revision + " was already received with another hash");
        }
        check(CaseAction.RECEIVE_SUBMISSION, actor, CasePolicy.DEFAULT);
        int expected = currentSubmission().map(s -> s.revision() + 1).orElse(1);
        if (revision != expected) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.REVISION_CONFLICT, "Expected revision " + expected + ", received " + revision);
        }
        Integer basedOn = revision > 1 ? revision - 1 : null;
        submissions.add(Submission.restore(
                revision,
                SubmissionStatus.SUBMITTED,
                basedOn,
                elements,
                submittedAt,
                submittedBy,
                seal,
                null,
                exchangeMode));
        reviewFindings.clear();
        transition(CaseAction.RECEIVE_SUBMISSION, CaseStatus.SUBMITTED, actor, now, null, revision);
        return true;
    }

    /** Re-verifies the signature of a submitted revision (returns the status). */
    @FunctionalInterface
    public interface SealVerifier {
        SignatureStatus verify(Seal seal);
    }

    /** Customer starts the review: SUBMITTED → IN_REVIEW. Guard: payload hash and signature of the revision. */
    public void startReview(SealVerifier verifier, Actor actor, Instant now) {
        startReview(verifier, CasePolicy.DEFAULT, actor, now);
    }

    /** Starts the review; technical clients only if the policy allows technical decisions. */
    public void startReview(SealVerifier verifier, CasePolicy policy, Actor actor, Instant now) {
        check(CaseAction.START_REVIEW, actor, policy);
        Submission current = currentSubmission().orElseThrow();
        Seal seal = current.seal();
        if (seal == null || !seal.hashValid()) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.PAYLOAD_HASH_MISMATCH, "Payload of revision " + current.revision() + " is corrupt");
        }
        SignatureStatus signatureStatus = verifier.verify(seal);
        current.signatureStatus(signatureStatus);
        if (signatureStatus == SignatureStatus.INVALID) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.SIGNATURE_INVALID, "Signature of revision " + current.revision() + " is invalid");
        }
        reviewFindings.clear();
        transition(CaseAction.START_REVIEW, CaseStatus.IN_REVIEW, actor, now, null, current.revision());
    }

    /** Records the (unsigned, intermediate) review result of an element. */
    public void recordFinding(ElementFinding finding, CasePolicy policy, Actor actor, Instant now) {
        check(CaseAction.REVIEW, actor, policy);
        Submission current = currentSubmission().orElseThrow();
        if (current.evidence(finding.code()).isEmpty()
                && request.element(finding.code()).isEmpty()) {
            throw new NotFoundException("Element", finding.code());
        }
        reviewFindings.put(finding.code(), finding);
        record(CaseAction.REVIEW, status, status, actor, now, null, current.revision(), finding.code());
    }

    /** Builds the decision payload, signs it and returns the seal. */
    @FunctionalInterface
    public interface DecisionSealer {
        Seal seal(ApprovalCase approvalCase, Submission submission, Decision unsealed);
    }

    /**
     * Customer decides: IN_REVIEW → APPROVED | CONDITIONALLY_APPROVED | REJECTED. Mandatory justifications are
     * checked ({@link DecisionRules}); the decision is sealed and immutable afterwards.
     */
    public Decision decide(
            DecisionResult result,
            List<String> conditions,
            LocalDate conditionDueDate,
            String comment,
            boolean confirmed,
            DecisionSealer sealer,
            CasePolicy policy,
            Actor actor,
            Instant now,
            LocalDate today) {
        check(CaseAction.DECIDE, actor, policy);
        requireConfirmation(confirmed);
        Objects.requireNonNull(result, "result");
        List<String> cleanConditions = conditions == null
                ? List.of()
                : conditions.stream().filter(c -> c != null && !c.isBlank()).toList();
        DecisionRules.check(result, cleanConditions, conditionDueDate, reviewFindings.values(), today);
        boolean conditional = result == DecisionResult.CONDITIONALLY_APPROVED;
        Submission current = currentSubmission().orElseThrow();
        var unsealed = new Decision(
                current.revision(),
                result,
                conditional ? cleanConditions : List.of(),
                conditional ? conditionDueDate : null,
                comment,
                List.copyOf(reviewFindings.values()),
                now,
                actor.name(),
                null);
        Seal seal = sealer.seal(this, current, unsealed);
        Decision decision = withSeal(unsealed, seal);
        current.decide(decision);
        transition(CaseAction.DECIDE, result.status(), actor, now, comment, current.revision());
        return decision;
    }

    /**
     * Supplier receives the decision on a submitted revision. The decision must refer to the hash of that revision.
     * Idempotent for the same decision.
     *
     * @return {@code false} if the decision had already been received
     */
    public boolean receiveDecision(Decision decision, String submissionHash, Actor actor, Instant now) {
        Submission target = submissions.stream()
                .filter(s -> s.revision() == decision.revision())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Revision", decision.revision()));
        if (target.decision().isPresent()) {
            Decision existing = target.decision().get();
            if (existing.seal() != null
                    && decision.seal() != null
                    && existing.seal().payloadHash().equals(decision.seal().payloadHash())) {
                return false;
            }
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.REVISION_CONFLICT, "Revision " + decision.revision() + " is already decided");
        }
        check(CaseAction.RECEIVE_DECISION, actor, CasePolicy.DEFAULT);
        if (target != currentSubmission().orElseThrow() || target.seal() == null) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.REVISION_CONFLICT, "Revision " + decision.revision() + " is not the submitted one");
        }
        if (!target.seal().payloadHash().equals(submissionHash)) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.PAYLOAD_HASH_MISMATCH, "Decision refers to another submission hash");
        }
        target.decide(decision);
        transition(
                CaseAction.RECEIVE_DECISION,
                decision.result().status(),
                actor,
                now,
                decision.comment(),
                decision.revision());
        return true;
    }

    /**
     * Supplier: the customer withdrew the request. Idempotent (already cancelled = no effect).
     *
     * @return {@code false} if the case was already cancelled
     */
    public boolean receiveWithdrawal(String reason, Actor actor, Instant now) {
        if (status == CaseStatus.CANCELLED) {
            return false;
        }
        check(CaseAction.RECEIVE_WITHDRAWAL, actor, CasePolicy.DEFAULT);
        transition(CaseAction.RECEIVE_WITHDRAWAL, CaseStatus.CANCELLED, actor, now, reason, null);
        return true;
    }

    /**
     * Status change of the partner's case: the customer learns that the supplier works on a revision ({@code
     * IN_PROGRESS}), the supplier learns that the customer reviews ({@code IN_REVIEW}). Duplicates and updates that
     * were overtaken by later messages (e.g. the submission arrived first) are ignored.
     *
     * @return {@code false} if the update had no effect (duplicate or stale)
     */
    public boolean receiveStatusUpdate(CaseStatus reported, int revision, Actor actor, Instant now) {
        int current = currentSubmission().map(Submission::revision).orElse(0);
        if (ownRole == CaseRole.CUSTOMER && reported == CaseStatus.IN_PROGRESS) {
            if (status == CaseStatus.IN_PROGRESS || revision <= current) {
                return false;
            }
        } else if (ownRole == CaseRole.SUPPLIER && reported == CaseStatus.IN_REVIEW) {
            // overtaken: already in review, decided, or a newer revision is being prepared
            if (status == CaseStatus.IN_REVIEW
                    || status.isDecided()
                    || status == CaseStatus.IN_PROGRESS
                    || revision < current) {
                return false;
            }
            if (revision != current) {
                throw ApprovalErrors.unprocessable(
                        ApprovalErrors.REVISION_CONFLICT,
                        "Review of revision " + revision + " but current is " + current);
            }
        } else {
            throw ApprovalErrors.invalidTransition(status, CaseAction.RECEIVE_STATUS_UPDATE);
        }
        check(CaseAction.RECEIVE_STATUS_UPDATE, actor, CasePolicy.DEFAULT);
        transition(CaseAction.RECEIVE_STATUS_UPDATE, reported, actor, now, null, revision);
        return true;
    }

    /**
     * Supplier assigns the own part to a received request that needs clarification ({@code PART_NOT_MAPPED}); the
     * clarification is removed.
     */
    public void assignPart(PartRef part, Actor actor, Instant now) {
        check(CaseAction.ASSIGN_PART, actor, CasePolicy.DEFAULT);
        if (clarification == null) {
            throw ApprovalErrors.unprocessable(
                    ApprovalErrors.NO_CLARIFICATION_PENDING, "Case " + caseNumber + " needs no clarification");
        }
        Objects.requireNonNull(part, "part");
        header = new CaseHeader(
                header.partner(),
                header.relationId(),
                part,
                header.partnerPartNumber(),
                header.reason(),
                header.level(),
                header.dueDate(),
                header.remarks(),
                header.externalReference());
        clarification = null;
        record(CaseAction.ASSIGN_PART, status, status, actor, now, null, null, null);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------------------

    /** User actions the actor may perform now (computed from status, own role, user roles and clarification). */
    public List<CaseAction> allowedActions(Actor actor, CasePolicy policy) {
        return allowedActions(ownRole, source, status, clarification, actor, policy);
    }

    /** User actions for a case in the given state (also used for list read models). */
    public static List<CaseAction> allowedActions(
            CaseRole ownRole,
            CaseSource source,
            CaseStatus status,
            Clarification clarification,
            Actor actor,
            CasePolicy policy) {
        return CaseStateMachine.allowedActions(ownRole, source, status, actor, policy).stream()
                .filter(action ->
                        clarification == null ? action != CaseAction.ASSIGN_PART : action != CaseAction.START_WORK)
                .toList();
    }

    /** Open clarification, {@code null} if none. */
    public Clarification clarification() {
        return clarification;
    }

    /** Whether the case is open and past its due date. */
    public boolean overdue(LocalDate today) {
        return status.isOpen() && header.dueDate().isBefore(today);
    }

    /** Latest revision. */
    public Optional<Submission> currentSubmission() {
        return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.getLast());
    }

    /** Revision by number. */
    public Submission submission(int revision) {
        return submissions.stream()
                .filter(s -> s.revision() == revision)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Revision", revision));
    }

    /** The draft revision. */
    public Submission draftSubmission() {
        return currentSubmission()
                .filter(Submission::draft)
                .orElseThrow(() -> new NotFoundException("Draft revision of case", caseNumber));
    }

    /** Latest decision. */
    public Optional<Decision> lastDecision() {
        for (int i = submissions.size() - 1; i >= 0; i--) {
            if (submissions.get(i).decision().isPresent()) {
                return submissions.get(i).decision();
            }
        }
        return Optional.empty();
    }

    /** Customer part number (derived from own role). */
    public String customerPartNumber() {
        return ownRole == CaseRole.CUSTOMER ? header.part().partNumber() : header.partnerPartNumber();
    }

    /** Supplier part number (derived from own role). */
    public String supplierPartNumber() {
        return ownRole == CaseRole.SUPPLIER ? header.part().partNumber() : header.partnerPartNumber();
    }

    /** Checks the client's expected version ({@code If-Match}). */
    public void checkVersion(Long expectedVersion) {
        if (expectedVersion != null && metadata != null && expectedVersion != metadata.version()) {
            throw new VersionConflictException("Approval case", id, expectedVersion, metadata.version());
        }
    }

    /** Collected changes since loading (cleared by {@link #pullChanges()}). */
    public List<CaseChange> pullChanges() {
        List<CaseChange> pulled = List.copyOf(changes);
        changes.clear();
        return pulled;
    }

    public UUID id() {
        return id;
    }

    public String caseNumber() {
        return caseNumber;
    }

    public UUID exchangeId() {
        return exchangeId;
    }

    public CaseRole ownRole() {
        return ownRole;
    }

    public CaseSource source() {
        return source;
    }

    public CaseStatus status() {
        return status;
    }

    public CaseHeader header() {
        return header;
    }

    public ApprovalRequest request() {
        return request;
    }

    public List<Submission> submissions() {
        return List.copyOf(submissions);
    }

    public List<ElementFinding> reviewFindings() {
        return List.copyOf(reviewFindings.values());
    }

    public List<TimelineEntry> timeline() {
        return List.copyOf(timeline);
    }

    /** Metadata, {@code null} before the first save. */
    public CaseMetadata metadata() {
        return metadata;
    }

    public Long version() {
        return metadata == null ? null : metadata.version();
    }

    // ---------------------------------------------------------------------------------------------------------

    private void check(CaseAction action, Actor actor, CasePolicy policy) {
        CaseStateMachine.check(action, ownRole, source, status, actor, policy);
    }

    private void transition(
            CaseAction action, CaseStatus target, Actor actor, Instant now, String comment, Integer revision) {
        CaseStatus from = status;
        status = target;
        record(action, from, target, actor, now, comment, revision, null);
    }

    private void record(
            CaseAction action,
            CaseStatus from,
            CaseStatus to,
            Actor actor,
            Instant now,
            String comment,
            Integer revision,
            String elementCode) {
        var change = new CaseChange(action, from, to, now, actor.name(), comment, revision, elementCode);
        changes.add(change);
        if (change.transition()) {
            timeline.add(new TimelineEntry(Ids.newId(), now, actor.name(), from, to, action, comment));
        }
    }

    private RequestedElement requestedElement(String code) {
        return request.element(code).orElseThrow(() -> new NotFoundException("Element", code));
    }

    private static void requireElements(List<RequestedElement> elements) {
        if (elements == null || elements.isEmpty()) {
            throw ApprovalErrors.unprocessable(ApprovalErrors.NO_ELEMENTS, "The request has no elements");
        }
    }

    private static void requireConfirmation(boolean confirmed) {
        if (!confirmed) {
            throw new ValidationException(
                    ApprovalErrors.CONFIRMATION_REQUIRED,
                    List.of(new FieldViolation(
                            "confirmation", ApprovalErrors.CONFIRMATION_REQUIRED, "explicit confirmation required")));
        }
    }

    private static Decision withSeal(Decision d, Seal seal) {
        return new Decision(
                d.revision(),
                d.result(),
                d.conditions(),
                d.conditionDueDate(),
                d.comment(),
                d.elementFindings(),
                d.decidedAt(),
                d.decidedBy(),
                seal);
    }
}
