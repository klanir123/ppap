package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.ValueValidator;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the supplier's submission package (UC-05): draft revision, completeness, submit, new revision. */
@Service
@Transactional
public class SubmissionService {

    static final int COMMENT_MAX = 4000;

    private final ApprovalContext context;
    private final DocumentRefs documents;
    private final PayloadSealing sealing;
    private final ValueValidator validator;

    public SubmissionService(
            ApprovalContext context, DocumentRefs documents, PayloadSealing sealing, ValueValidator validator) {
        this.context = context;
        this.documents = documents;
        this.sealing = sealing;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<SubmissionView> list(UUID caseId) {
        ApprovalCase c = context.load(caseId);
        return c.submissions().stream().map(s -> view(c, s)).toList();
    }

    @Transactional(readOnly = true)
    public SubmissionView get(UUID caseId, int revision) {
        ApprovalCase c = context.load(caseId);
        return view(c, c.submission(revision));
    }

    @Transactional(readOnly = true)
    public SubmissionView draft(UUID caseId) {
        ApprovalCase c = context.load(caseId);
        return view(c, c.draftSubmission());
    }

    /** Updates self assessment, values and comment of an element of the draft. */
    public SubmissionView updateEvidence(
            UUID caseId, String code, CaseCommands.Evidence command, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        var v = new Validation();
        SelfAssessment assessment =
                v.optionalEnum("selfAssessment", command.selfAssessment(), SelfAssessment.class, null);
        String comment = v.optional("comment", command.comment(), COMMENT_MAX);
        v.throwIfInvalid();
        c.updateEvidence(code, assessment, command.values(), comment, actor, context.now());
        ApprovalCase saved = context.save(c);
        return view(saved, saved.draftSubmission());
    }

    public SubmissionView attachDocument(UUID caseId, String code, UUID documentId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        if (documentId == null) {
            throw ValidationException.of("documentId", ViolationCodes.REQUIRED, "must not be blank");
        }
        c.attachEvidenceDocument(code, documents.resolve(documentId), actor, context.now());
        ApprovalCase saved = context.save(c);
        return view(saved, saved.draftSubmission());
    }

    public Long detachDocument(UUID caseId, String code, UUID documentId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        c.detachEvidenceDocument(code, documentId, actor, context.now());
        return context.save(c).version();
    }

    /** Submits the draft: complete → sealed (canonical payload, hash, JWS) → SUBMITTED, immutable. */
    public SubmissionView submit(UUID caseId, Boolean confirmation, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        Submission submitted =
                c.submit(Boolean.TRUE.equals(confirmation), validator, sealing::sealSubmission, actor, context.now());
        ApprovalCase saved = context.save(c);
        return view(saved, saved.submission(submitted.revision()));
    }

    /** New draft revision after rejection or conditional approval. */
    public SubmissionView newRevision(UUID caseId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        Submission next = c.newRevision(actor, context.now());
        ApprovalCase saved = context.save(c);
        return view(saved, saved.submission(next.revision()));
    }

    /** Canonical exchange payload of a submitted revision. */
    @Transactional(readOnly = true)
    public Seal payload(UUID caseId, int revision) {
        Submission s = context.load(caseId).submission(revision);
        if (s.seal() == null) {
            throw new NotFoundException("Payload of revision", revision);
        }
        return s.seal();
    }

    SubmissionView view(ApprovalCase c, Submission s) {
        return new SubmissionView(c, s, c.completeness(s, validator));
    }

    private ApprovalCase loadChecked(UUID id, Long expectedVersion) {
        ApprovalCase c = context.load(id);
        c.checkVersion(expectedVersion);
        return c;
    }
}
