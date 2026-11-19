package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A revision of the supplier's submission: an editable draft or a submitted, sealed and immutable revision with an
 * optional decision. Part of the {@link ApprovalCase} aggregate.
 */
public final class Submission {

    private final int revision;
    private final Integer basedOnRevision;
    private final Map<String, ElementEvidence> elements;
    private SubmissionStatus status;
    private Instant submittedAt;
    private String submittedBy;
    private Seal seal;
    private Decision decision;

    private Submission(
            int revision,
            SubmissionStatus status,
            Integer basedOnRevision,
            List<ElementEvidence> elements,
            Instant submittedAt,
            String submittedBy,
            Seal seal,
            Decision decision) {
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be >= 1");
        }
        this.revision = revision;
        this.status = Objects.requireNonNull(status, "status");
        this.basedOnRevision = basedOnRevision;
        this.elements = new LinkedHashMap<>();
        elements.forEach(e -> this.elements.put(e.code(), e));
        this.submittedAt = submittedAt;
        this.submittedBy = submittedBy;
        this.seal = seal;
        this.decision = decision;
    }

    /** New draft revision. */
    static Submission draft(int revision, Integer basedOnRevision, List<ElementEvidence> elements) {
        return new Submission(revision, SubmissionStatus.DRAFT, basedOnRevision, elements, null, null, null, null);
    }

    /** Restores a persisted revision. */
    public static Submission restore(
            int revision,
            SubmissionStatus status,
            Integer basedOnRevision,
            List<ElementEvidence> elements,
            Instant submittedAt,
            String submittedBy,
            Seal seal,
            Decision decision) {
        return new Submission(revision, status, basedOnRevision, elements, submittedAt, submittedBy, seal, decision);
    }

    public int revision() {
        return revision;
    }

    public SubmissionStatus status() {
        return status;
    }

    public boolean draft() {
        return status == SubmissionStatus.DRAFT;
    }

    public Integer basedOnRevision() {
        return basedOnRevision;
    }

    /** Evidence in request order. */
    public List<ElementEvidence> elements() {
        return List.copyOf(elements.values());
    }

    Map<String, ElementEvidence> evidenceByCode() {
        return Map.copyOf(elements);
    }

    public Optional<ElementEvidence> evidence(String code) {
        return Optional.ofNullable(elements.get(code));
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public String submittedBy() {
        return submittedBy;
    }

    /** Seal of a submitted revision, {@code null} for drafts. */
    public Seal seal() {
        return seal;
    }

    public Optional<Decision> decision() {
        return Optional.ofNullable(decision);
    }

    /** Unsealed view of this draft as it would be submitted (used to build the payload). */
    public Submission asSubmitted(Instant at, String by) {
        return new Submission(revision, SubmissionStatus.SUBMITTED, basedOnRevision, elements(), at, by, null, null);
    }

    void replace(ElementEvidence evidence) {
        requireDraft();
        elements.put(evidence.code(), evidence);
    }

    void seal(Instant at, String by, Seal newSeal) {
        requireDraft();
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedAt = Objects.requireNonNull(at);
        this.submittedBy = Objects.requireNonNull(by);
        this.seal = Objects.requireNonNull(newSeal);
    }

    void decide(Decision newDecision) {
        if (status != SubmissionStatus.SUBMITTED || decision != null) {
            throw new IllegalStateException("revision " + revision + " cannot be decided");
        }
        this.decision = Objects.requireNonNull(newDecision);
    }

    void signatureStatus(SignatureStatus signatureStatus) {
        if (seal != null) {
            seal = seal.withSignatureStatus(signatureStatus);
        }
    }

    /** Copies the evidence into a new draft revision (after rejection / conditional approval). */
    Submission nextDraft(List<RequestedElement> requested) {
        List<ElementEvidence> copy = new ArrayList<>();
        for (RequestedElement element : requested) {
            copy.add(Optional.ofNullable(elements.get(element.code())).orElseGet(() -> ElementEvidence.empty(element)));
        }
        return draft(revision + 1, revision, copy);
    }

    private void requireDraft() {
        if (status != SubmissionStatus.DRAFT) {
            throw new IllegalStateException("revision " + revision + " is submitted and immutable");
        }
    }
}
