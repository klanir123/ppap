package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.application.ActivityView;
import com.msg.ppap.approval.application.CaseView;
import com.msg.ppap.approval.application.ExcelPreview;
import com.msg.ppap.approval.application.SubSupplierCandidate;
import com.msg.ppap.approval.application.SubmissionView;
import com.msg.ppap.approval.application.SummaryView;
import com.msg.ppap.approval.application.port.out.CaseStatistics;
import com.msg.ppap.approval.application.port.out.CaseSummary;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ElementFinding;
import com.msg.ppap.approval.domain.FormTemplate;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SubSupplierReference;
import com.msg.ppap.approval.domain.Submission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Mapping of application views to REST DTOs. */
final class ApprovalDtos {

    private ApprovalDtos() {}

    static ApprovalCaseDto summary(SummaryView view) {
        CaseSummary s = view.summary();
        return new ApprovalCaseDto(
                s.id(),
                s.caseNumber(),
                s.exchangeId(),
                s.ownRole(),
                s.source(),
                s.status(),
                new CasePartnerDto(s.partner().bpnl(), s.partner().name()),
                s.relationId(),
                part(s.part()),
                s.partnerPartNumber(),
                s.customerPartNumber(),
                s.supplierPartNumber(),
                ref(s.reason()),
                ref(s.level()),
                s.dueDate(),
                s.overdue(view.today()),
                s.remarks(),
                null,
                s.currentRevision(),
                s.requestedAt(),
                s.submittedAt(),
                s.decidedAt(),
                s.lastDecision(),
                view.allowedActions(),
                s.version(),
                s.createdAt(),
                s.updatedAt(),
                ClarificationDto.of(s.clarification()),
                LastOutboundDto.of(view.lastOutbound()),
                s.parentCaseId());
    }

    static ApprovalCaseDetailDto detail(CaseView view) {
        ApprovalCase c = view.approvalCase();
        var h = c.header();
        var m = c.metadata();
        var lastSubmitted = c.submissions().stream()
                .map(Submission::submittedAt)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> b)
                .orElse(null);
        var last = c.lastDecision();
        return new ApprovalCaseDetailDto(
                c.id(),
                c.caseNumber(),
                c.exchangeId(),
                c.ownRole(),
                c.source(),
                c.status(),
                new CasePartnerDto(h.partner().bpnl(), h.partner().name()),
                h.relationId(),
                part(h.part()),
                h.partnerPartNumber(),
                c.customerPartNumber(),
                c.supplierPartNumber(),
                ref(h.reason()),
                ref(h.level()),
                h.dueDate(),
                c.overdue(view.today()),
                h.remarks(),
                h.externalReference(),
                c.currentSubmission().map(Submission::revision).orElse(null),
                c.request().requestedAt(),
                lastSubmitted,
                last.map(Decision::decidedAt).orElse(null),
                view.allowedActions(),
                m == null ? 0 : m.version(),
                m == null ? null : m.createdAt(),
                m == null ? null : m.updatedAt(),
                new CaseRequestDto(
                        c.request().requestedAt(),
                        c.request().requestedBy(),
                        c.request().elements().stream()
                                .map(e -> new RequestedElementDto(
                                        e.code(),
                                        e.requirement(),
                                        e.note(),
                                        new ElementSnapshotDto(
                                                e.snapshot().nameDe(),
                                                e.snapshot().nameEn(),
                                                e.snapshot().evidenceType(),
                                                e.snapshot().valueSchema())))
                                .toList(),
                        documents(c.request().attachments(), view.transfers())),
                c.submissions().stream().map(ApprovalDtos::submissionSummary).toList(),
                last.map(ApprovalDtos::decision).orElse(null),
                findings(c.reviewFindings()),
                c.timeline().stream()
                        .map(t -> new TimelineEntryDto(
                                t.at(),
                                t.actor(),
                                view.actorDisplay(t.actor()),
                                t.from(),
                                t.to(),
                                t.action(),
                                t.comment()))
                        .toList(),
                view.draftCompleteness() == null ? null : completeness(view.draftCompleteness()),
                ClarificationDto.of(c.clarification()),
                LastOutboundDto.of(view.lastOutbound()),
                view.parent() == null
                        ? null
                        : new ParentCaseDto(
                                view.parent().id(),
                                view.parent().caseNumber(),
                                partner(view.parent().partner())),
                view.children().stream()
                        .map(child -> new ChildCaseDto(
                                child.id(),
                                child.caseNumber(),
                                partner(child.partner()),
                                child.partNumber(),
                                child.status(),
                                child.lastDecisionResult()))
                        .toList());
    }

    static CasePartnerDto partner(com.msg.ppap.approval.domain.PartnerRef partner) {
        return new CasePartnerDto(partner.bpnl(), partner.name());
    }

    static SubSupplierReferenceDto reference(SubSupplierReference r) {
        return new SubSupplierReferenceDto(
                r.childCaseId(),
                r.subSupplierBpnl(),
                r.subSupplierName(),
                r.exchangeId(),
                r.caseNumber(),
                r.partNumber(),
                r.partName(),
                r.supplierPartNumber(),
                r.revision(),
                r.decisionResult(),
                r.decidedAt(),
                r.decisionHash(),
                r.conditionDueDate());
    }

    static SubSupplierCandidateDto candidate(SubSupplierCandidate c) {
        return new SubSupplierCandidateDto(
                c.caseId(),
                c.caseNumber(),
                c.exchangeId(),
                partner(c.subSupplier()),
                c.partNumber(),
                c.partName(),
                c.supplierPartNumber(),
                c.status(),
                c.revision(),
                c.decisionResult(),
                c.decidedAt(),
                c.decisionHash(),
                c.conditionDueDate(),
                c.referenceable());
    }

    static ActivityDto activity(ActivityView a) {
        return new ActivityDto(
                a.at(),
                a.caseId(),
                a.caseNumber(),
                a.action(),
                a.actor(),
                a.actorDisplay(),
                partner(a.partner()),
                a.fromStatus(),
                a.toStatus());
    }

    /** Own part, {@code null} while not mapped. */
    static CasePartDto part(PartRef part) {
        return part == null || !part.mapped()
                ? null
                : new CasePartDto(part.id(), part.partNumber(), part.nameDe(), part.nameEn());
    }

    static SubmissionSummaryDto submissionSummary(Submission s) {
        Seal seal = s.seal();
        return new SubmissionSummaryDto(
                s.revision(),
                s.status(),
                s.submittedAt(),
                s.submittedBy(),
                seal == null ? null : seal.payloadHash(),
                seal == null ? null : signature(seal),
                s.decision().map(Decision::result).orElse(null),
                s.basedOnRevision(),
                s.exchangeMode());
    }

    static SubmissionDto submission(SubmissionView view) {
        Submission s = view.submission();
        Completeness completeness = view.completeness();
        Seal seal = s.seal();
        Map<String, ElementFinding> findings = new LinkedHashMap<>();
        s.decision().ifPresent(d -> d.elementFindings().forEach(f -> findings.put(f.code(), f)));
        if (s.decision().isEmpty()
                && view.approvalCase()
                        .currentSubmission()
                        .filter(cur -> cur.revision() == s.revision())
                        .isPresent()) {
            view.approvalCase().reviewFindings().forEach(f -> findings.put(f.code(), f));
        }
        return new SubmissionDto(
                s.revision(),
                s.status(),
                s.submittedAt(),
                s.submittedBy(),
                s.elements().stream()
                        .map(e -> {
                            var issues = completeness.issues(e.code()).stream()
                                    .map(i -> new IssueDto(i.code(), i.message(), i.pointer()))
                                    .toList();
                            ElementFinding finding = findings.get(e.code());
                            return new ElementEvidenceDto(
                                    e.code(),
                                    e.requirement(),
                                    e.selfAssessment(),
                                    e.values(),
                                    e.comment(),
                                    documents(e.documents(), view.transfers()),
                                    issues.isEmpty(),
                                    issues,
                                    finding == null
                                            ? null
                                            : new FindingDto(finding.code(), finding.result(), finding.comment()));
                        })
                        .toList(),
                completeness(completeness),
                seal == null ? null : seal.payloadHash(),
                seal == null ? null : signature(seal),
                s.decision().map(ApprovalDtos::decision).orElse(null),
                s.basedOnRevision(),
                s.exchangeMode(),
                s.subSupplierReferences().stream().map(ApprovalDtos::reference).toList());
    }

    static CompletenessDto completeness(Completeness c) {
        return new CompletenessDto(
                c.complete(),
                c.requiredTotal(),
                c.requiredDone(),
                c.issues().stream()
                        .map(i -> new CompletenessIssueDto(i.elementCode(), i.code(), i.message(), i.pointer()))
                        .toList(),
                c.hints().stream()
                        .map(i -> new CompletenessIssueDto(i.elementCode(), i.code(), i.message(), i.pointer()))
                        .toList());
    }

    static DecisionDto decision(Decision d) {
        return new DecisionDto(
                d.revision(),
                d.result(),
                d.conditions().stream().map(ConditionDto::new).toList(),
                d.conditionDueDate(),
                d.comment(),
                findings(d.elementFindings()),
                d.decidedAt(),
                d.decidedBy(),
                d.seal() == null ? null : d.seal().payloadHash(),
                d.seal() == null ? null : signature(d.seal()));
    }

    static List<FindingDto> findings(List<ElementFinding> findings) {
        return findings.stream()
                .map(f -> new FindingDto(f.code(), f.result(), f.comment()))
                .toList();
    }

    static SignatureDto signature(Seal seal) {
        var s = seal.signature();
        return new SignatureDto(s.alg(), s.kid(), s.jws(), s.status());
    }

    /**
     * Document references with their transfer state: own documents are {@code LOCAL}; partner documents carry the
     * state of their transfer (id set once fetched and verified) or {@code NOT_AVAILABLE} without transfer.
     */
    static List<DocumentRefDto> documents(
            List<DocumentRef> refs, Map<String, DocumentTransferStatus.Transfer> transfers) {
        return refs.stream()
                .map(d -> {
                    if (d.documentId() != null) {
                        return dto(d, d.documentId(), DocumentRefDto.TransferStatus.LOCAL, true, null);
                    }
                    DocumentTransferStatus.Transfer t = transfers.get(d.sha256());
                    if (t == null) {
                        return dto(d, null, DocumentRefDto.TransferStatus.NOT_AVAILABLE, false, null);
                    }
                    var status = DocumentRefDto.TransferStatus.valueOf(t.status());
                    return dto(
                            d,
                            t.documentId(),
                            status,
                            t.hashVerified(),
                            status == DocumentRefDto.TransferStatus.FAILED ? t.errorCode() : null);
                })
                .toList();
    }

    private static DocumentRefDto dto(
            DocumentRef d,
            java.util.UUID id,
            DocumentRefDto.TransferStatus status,
            boolean hashVerified,
            String transferError) {
        return new DocumentRefDto(
                id,
                d.fileName(),
                d.mediaType(),
                d.size(),
                d.sha256(),
                d.uploadedAt(),
                d.uploadedBy(),
                status,
                hashVerified,
                transferError);
    }

    static CatalogRefDto ref(CatalogRef ref) {
        return new CatalogRefDto(ref.code(), ref.nameDe(), ref.nameEn());
    }

    static CaseStatsDto stats(CaseStatistics s) {
        return new CaseStatsDto(
                s.open(),
                s.overdue(),
                s.inReview(),
                s.approvedLast30Days(),
                s.averageLeadTimeDays(),
                s.approvalRate(),
                new TreeMap<>(s.byStatus()),
                s.byPartner().stream()
                        .map(p -> new PartnerStatsDto(
                                p.bpnl(), p.name(), p.open(), p.overdue(), p.exchangeHealth(), p.lastExchangeAt()))
                        .toList(),
                s.needsClarification(),
                s.blockingChildren());
    }

    static FormTemplateDto template(FormTemplate t) {
        return new FormTemplateDto(
                t.code(),
                t.nameDe(),
                t.nameEn(),
                new FormTemplateMarkerDto(
                        t.marker().sheet(), t.marker().cell(), t.marker().value()),
                t.header().stream()
                        .map(h -> new FormTemplateHeaderFieldDto(h.field(), h.sheet(), h.cell()))
                        .toList(),
                new FormTemplateElementTableDto(
                        t.elementTable().sheet(),
                        t.elementTable().firstRow(),
                        t.elementTable().lastRow(),
                        new TreeMap<>(t.elementTable().columns())),
                t.selfAssessmentValues(),
                t.active());
    }

    static FormImportPreviewDto preview(ExcelPreview p) {
        Map<String, FormHeaderCellDto> header = new LinkedHashMap<>();
        p.header().forEach((field, cell) -> header.put(field, new FormHeaderCellDto(cell.value(), cell.cell())));
        return new FormImportPreviewDto(
                p.templateCode(),
                header,
                p.elements().stream()
                        .map(e -> new FormElementDto(
                                e.code(),
                                e.selfAssessment(),
                                e.comment(),
                                e.values(),
                                e.cell(),
                                e.status(),
                                messages(e.messages())))
                        .toList(),
                p.unknownCodes(),
                messages(p.warnings()));
    }

    private static List<FormMessageDto> messages(List<ExcelPreview.Message> messages) {
        return messages.stream()
                .map(m -> new FormMessageDto(m.code(), m.message()))
                .toList();
    }
}
