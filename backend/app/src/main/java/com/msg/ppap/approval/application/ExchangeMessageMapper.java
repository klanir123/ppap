package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ElementEvidence;
import com.msg.ppap.approval.domain.ElementFinding;
import com.msg.ppap.approval.domain.FindingResult;
import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.DocumentReference;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps the approval aggregate to the version-neutral exchange messages of the semantics module and back. The domain
 * itself never sees exchange structures.
 */
@Component
public class ExchangeMessageMapper {

    private final ApprovalContext context;

    public ExchangeMessageMapper(ApprovalContext context) {
        this.context = context;
    }

    public String customerBpnl(ApprovalCase c) {
        return c.ownRole() == CaseRole.CUSTOMER
                ? context.tenantBpnl()
                : c.header().partner().bpnl();
    }

    public String supplierBpnl(ApprovalCase c) {
        return c.ownRole() == CaseRole.SUPPLIER
                ? context.tenantBpnl()
                : c.header().partner().bpnl();
    }

    public RequestMessage request(ApprovalCase c) {
        var h = c.header();
        return new RequestMessage(
                c.exchangeId(),
                c.caseNumber(),
                customerBpnl(c),
                supplierBpnl(c),
                c.customerPartNumber(),
                c.supplierPartNumber(),
                h.part().nameDe(),
                new RequestMessage.CodeName(
                        h.reason().code(), h.reason().nameDe(), h.reason().nameEn()),
                new RequestMessage.CodeName(
                        h.level().code(), h.level().nameDe(), h.level().nameEn()),
                h.dueDate(),
                h.remarks(),
                c.request().requestedAt(),
                c.request().requestedBy(),
                c.request().elements().stream()
                        .map(e -> new RequestMessage.Element(
                                e.code(),
                                e.requirement().name(),
                                e.note(),
                                new RequestMessage.Snapshot(
                                        e.snapshot().nameDe(),
                                        e.snapshot().nameEn(),
                                        e.snapshot().evidenceType().name(),
                                        e.snapshot().valueSchema())))
                        .toList(),
                c.request().attachments().stream()
                        .map(ExchangeMessageMapper::reference)
                        .toList());
    }

    public SubmissionMessage submission(ApprovalCase c, Submission s) {
        var h = c.header();
        return new SubmissionMessage(
                c.exchangeId(),
                c.caseNumber(),
                s.revision(),
                customerBpnl(c),
                supplierBpnl(c),
                c.customerPartNumber(),
                c.supplierPartNumber(),
                h.reason().code(),
                h.level().code(),
                s.elements().stream()
                        .map(e -> new SubmissionMessage.Element(
                                e.code(),
                                e.requirement().name(),
                                e.selfAssessment() == null
                                        ? null
                                        : e.selfAssessment().name(),
                                e.values(),
                                e.comment(),
                                e.documents().stream()
                                        .map(ExchangeMessageMapper::reference)
                                        .toList()))
                        .toList(),
                List.of(),
                s.submittedAt(),
                s.submittedBy());
    }

    public DecisionMessage decision(ApprovalCase c, Submission s, Decision d) {
        return new DecisionMessage(
                c.exchangeId(),
                c.caseNumber(),
                d.revision(),
                s.seal().payloadHash(),
                d.result().name(),
                d.conditions(),
                d.conditionDueDate(),
                d.comment(),
                d.elementFindings().stream()
                        .map(f ->
                                new DecisionMessage.Finding(f.code(), f.result().name(), f.comment()))
                        .toList(),
                d.decidedAt(),
                d.decidedBy());
    }

    /** Evidence of a received submission (documents known by metadata and hash only). */
    public static List<ElementEvidence> evidence(SubmissionMessage message) {
        return message.elements().stream()
                .map(e -> new ElementEvidence(
                        e.code(),
                        Requirement.valueOf(e.requirement()),
                        e.selfAssessment() == null ? null : SelfAssessment.valueOf(e.selfAssessment()),
                        e.values(),
                        e.comment(),
                        e.documents().stream()
                                .map(d -> new DocumentRef(
                                        null, d.fileName(), d.mediaType(), d.size(), d.sha256(), null, null))
                                .toList()))
                .toList();
    }

    /** Findings of a received decision. */
    public static List<ElementFinding> findings(DecisionMessage message) {
        return message.elementFindings().stream()
                .map(f -> new ElementFinding(f.code(), FindingResult.valueOf(f.result()), f.comment()))
                .toList();
    }

    static DocumentReference reference(DocumentRef ref) {
        return new DocumentReference(ref.fileName(), ref.mediaType(), ref.size(), ref.sha256());
    }
}
