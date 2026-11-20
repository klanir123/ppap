package com.msg.ppap.approval.application;

import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.PartnerKeyRefresh;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.ApprovalErrors;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.SignatureStatus;
import com.msg.ppap.approval.domain.SubSupplierReference;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.ValueValidator;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the supplier's submission package (UC-05): draft revision, completeness, submit, new revision. */
@Service
@Transactional
public class SubmissionService {

    static final int COMMENT_MAX = 4000;

    /** A referenced child case is not an own, approved sub-supplier case (UC-09, AK-09-1). */
    public static final String SUB_SUPPLIER_REFERENCE_INVALID = "SUB_SUPPLIER_REFERENCE_INVALID";

    /** A document received from a sub-supplier must not be attached to an own submission (UC-09, AK-09-2). */
    public static final String SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE = DocumentRefs.RECEIVED_DOCUMENT_NOT_SHAREABLE;

    private final ApprovalContext context;
    private final DocumentRefs documents;
    private final PayloadSealing sealing;
    private final ValueValidator validator;
    private final MasterdataQueries masterdata;
    private final ObjectProvider<DocumentTransferStatus> transfers;
    private final ObjectProvider<PartnerKeyRefresh> keyRefresh;
    private final CascadeQueries cascade;

    @SuppressWarnings("java:S107")
    public SubmissionService(
            ApprovalContext context,
            DocumentRefs documents,
            PayloadSealing sealing,
            ValueValidator validator,
            MasterdataQueries masterdata,
            ObjectProvider<DocumentTransferStatus> transfers,
            ObjectProvider<PartnerKeyRefresh> keyRefresh,
            CascadeQueries cascade) {
        this.keyRefresh = keyRefresh;
        this.cascade = cascade;
        this.context = context;
        this.documents = documents;
        this.sealing = sealing;
        this.validator = validator;
        this.masterdata = masterdata;
        this.transfers = transfers;
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
        // UC-09, AK-09-2: document records received from a partner never leave the tenant (provenance, not content)
        DocumentRef document = documents.resolveShareable(documentId);
        c.attachEvidenceDocument(code, document, actor, context.now());
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
        Submission submitted = c.submit(
                Boolean.TRUE.equals(confirmation),
                validator,
                sealing::sealSubmission,
                exchangeMode(c),
                actor,
                context.now());
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

    /** Sub-supplier candidates of a supplier case (UC-09): its decided child cases. */
    @Transactional(readOnly = true)
    public List<SubSupplierCandidate> candidates(UUID caseId) {
        ApprovalCase c = context.load(caseId);
        return cascade.childSummaries(c).stream()
                .filter(child -> child.status().isDecided())
                .map(child -> candidate(context.load(child.id())))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    /**
     * Replaces the sub-supplier references of the draft (UC-09, If-Match): every id must be an own child case of
     * this case with an approval (AK-09-1); the reference data is taken from the child's last decision.
     */
    public SubmissionView replaceSubSupplierReferences(UUID caseId, List<UUID> childCaseIds, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        List<SubSupplierReference> references = new ArrayList<>();
        var v = new Validation();
        List<UUID> ids = childCaseIds == null ? List.of() : childCaseIds;
        for (int i = 0; i < ids.size(); i++) {
            UUID childId = ids.get(i);
            Optional<SubSupplierCandidate> candidate = childId == null
                    ? Optional.empty()
                    : context.repository()
                            .findById(childId)
                            .filter(child -> c.id().equals(child.parentCaseId()))
                            .flatMap(this::candidate);
            if (candidate.isEmpty()) {
                v.add("childCaseIds[" + i + "]", SUB_SUPPLIER_REFERENCE_INVALID, "not an own sub-supplier case");
            } else if (!candidate.get().referenceable()) {
                v.add(
                        "childCaseIds[" + i + "]",
                        SUB_SUPPLIER_REFERENCE_INVALID,
                        "sub-supplier case " + candidate.get().caseNumber() + " is not approved");
            } else {
                references.add(candidate.get().reference());
            }
        }
        if (!v.violations().isEmpty()) {
            throw ApprovalErrors.rule(SUB_SUPPLIER_REFERENCE_INVALID, v.violations());
        }
        c.replaceSubSupplierReferences(references, actor, context.now());
        ApprovalCase saved = context.save(c);
        return view(saved, saved.draftSubmission());
    }

    /**
     * Verifies the signature of a received revision again; an {@code UNVERIFIED} signature triggers a fetch of the
     * partner's keys first (UC-08, docs/api/cascade-transparency-api.md §7).
     */
    public SubmissionView verifySignature(UUID caseId, int revision) {
        Actor actor = context.actor();
        ApprovalCase c = context.load(caseId);
        String signer = signer(c);
        SignatureStatus status =
                c.reverifySignature(revision, seal -> sealing.verify(seal, signer), actor, context.now());
        PartnerKeyRefresh refresh = keyRefresh.getIfAvailable();
        if (status == SignatureStatus.UNVERIFIED && signer != null && refresh != null && refresh.refresh(signer)) {
            c.reverifySignature(revision, seal -> sealing.verify(seal, signer), actor, context.now());
        }
        ApprovalCase saved = context.save(c);
        return view(saved, saved.submission(revision));
    }

    /** BPNL of the signer of the revisions of a case: the partner on the customer side, the own tenant otherwise. */
    static String signer(ApprovalCase c) {
        return c.ownRole() == CaseRole.CUSTOMER ? c.header().partner().bpnl() : null;
    }

    private Optional<SubSupplierCandidate> candidate(ApprovalCase child) {
        return child.lastDecision()
                .map(d -> new SubSupplierCandidate(
                        child.id(),
                        child.caseNumber(),
                        child.header().partner(),
                        child.header().part().partNumber(),
                        child.header().part().nameDe(),
                        child.supplierPartNumber(),
                        child.status(),
                        d.revision(),
                        d.result(),
                        d.decidedAt(),
                        d.seal() == null ? null : d.seal().payloadHash(),
                        d.conditionDueDate(),
                        child.exchangeId()));
    }

    SubmissionView view(ApprovalCase c, Submission s) {
        DocumentTransferStatus status = transfers.getIfAvailable();
        Completeness completeness = c.completeness(s, validator);
        if (s.draft()) {
            completeness = completeness.withHints(cascade.pendingHints(cascade.childSummaries(c)));
        }
        return new SubmissionView(c, s, completeness, status == null ? Map.of() : status.transfers(c.id()));
    }

    /**
     * Effective exchange mode of a dataspace-linked supplier case: the mode of the relation at the time of the
     * submission (the supplier decides how it provides the package); {@code null} for local/manual cases.
     */
    ExchangeMode exchangeMode(ApprovalCase c) {
        if (c.ownRole() != CaseRole.SUPPLIER || c.source() != CaseSource.EXCHANGE) {
            return null;
        }
        String mode = c.header().relationId() == null
                ? null
                : masterdata
                        .findRelation(c.header().relationId())
                        .map(MasterdataQueries.RelationInfo::exchangeMode)
                        .orElse(null);
        return ExchangeMode.of(mode);
    }

    private ApprovalCase loadChecked(UUID id, Long expectedVersion) {
        ApprovalCase c = context.load(id);
        c.checkVersion(expectedVersion);
        return c;
    }
}
