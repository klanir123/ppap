package com.msg.ppap.approval.adapter.out.jpa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ElementEvidence;
import com.msg.ppap.approval.domain.ElementFinding;
import com.msg.ppap.approval.domain.ElementSnapshot;
import com.msg.ppap.approval.domain.EvidenceType;
import com.msg.ppap.approval.domain.FindingResult;
import com.msg.ppap.approval.domain.RequestedElement;
import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.SubSupplierReference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** JSON documents of the approval tables (persistence format, independent of the exchange format). */
final class ApprovalJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private ApprovalJson() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RequestedElementJson(
            String code,
            String requirement,
            String note,
            String nameDe,
            String nameEn,
            String evidenceType,
            Map<String, Object> valueSchema) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DocumentJson(
            UUID documentId,
            String fileName,
            String mediaType,
            long size,
            String sha256,
            Instant uploadedAt,
            String uploadedBy) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EvidenceJson(
            String code,
            String requirement,
            String selfAssessment,
            Object values,
            String comment,
            List<DocumentJson> documents) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FindingJson(String code, String result, String comment) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ReferenceJson(
            UUID childCaseId,
            String subSupplierBpnl,
            String subSupplierName,
            UUID exchangeId,
            String caseNumber,
            String partNumber,
            String partName,
            String supplierPartNumber,
            int revision,
            String decisionResult,
            Instant decidedAt,
            String decisionHash,
            java.time.LocalDate conditionDueDate) {}

    static String references(List<SubSupplierReference> references) {
        return write(references.stream()
                .map(r -> new ReferenceJson(
                        r.childCaseId(),
                        r.subSupplierBpnl(),
                        r.subSupplierName(),
                        r.exchangeId(),
                        r.caseNumber(),
                        r.partNumber(),
                        r.partName(),
                        r.supplierPartNumber(),
                        r.revision(),
                        r.decisionResult().name(),
                        r.decidedAt(),
                        r.decisionHash(),
                        r.conditionDueDate()))
                .toList());
    }

    static List<SubSupplierReference> references(String json) {
        return read(json, new TypeReference<List<ReferenceJson>>() {}).stream()
                .map(r -> new SubSupplierReference(
                        r.childCaseId(),
                        r.subSupplierBpnl(),
                        r.subSupplierName(),
                        r.exchangeId(),
                        r.caseNumber(),
                        r.partNumber(),
                        r.partName(),
                        r.supplierPartNumber(),
                        r.revision(),
                        DecisionResult.valueOf(r.decisionResult()),
                        r.decidedAt(),
                        r.decisionHash(),
                        r.conditionDueDate()))
                .toList();
    }

    static String requestElements(List<RequestedElement> elements) {
        return write(elements.stream()
                .map(e -> new RequestedElementJson(
                        e.code(),
                        e.requirement().name(),
                        e.note(),
                        e.snapshot().nameDe(),
                        e.snapshot().nameEn(),
                        e.snapshot().evidenceType().name(),
                        e.snapshot().valueSchema()))
                .toList());
    }

    static List<RequestedElement> requestElements(String json) {
        return read(json, new TypeReference<List<RequestedElementJson>>() {}).stream()
                .map(e -> new RequestedElement(
                        e.code(),
                        Requirement.valueOf(e.requirement()),
                        e.note(),
                        new ElementSnapshot(
                                e.nameDe(), e.nameEn(), EvidenceType.valueOf(e.evidenceType()), e.valueSchema())))
                .toList();
    }

    static String documents(List<DocumentRef> documents) {
        return write(documents.stream().map(ApprovalJson::document).toList());
    }

    static List<DocumentRef> documents(String json) {
        return read(json, new TypeReference<List<DocumentJson>>() {}).stream()
                .map(ApprovalJson::document)
                .toList();
    }

    static String evidence(List<ElementEvidence> evidence) {
        return write(evidence.stream()
                .map(e -> new EvidenceJson(
                        e.code(),
                        e.requirement().name(),
                        e.selfAssessment() == null ? null : e.selfAssessment().name(),
                        e.values(),
                        e.comment(),
                        e.documents().stream().map(ApprovalJson::document).toList()))
                .toList());
    }

    static List<ElementEvidence> evidence(String json) {
        return read(json, new TypeReference<List<EvidenceJson>>() {}).stream()
                .map(e -> new ElementEvidence(
                        e.code(),
                        Requirement.valueOf(e.requirement()),
                        e.selfAssessment() == null ? null : SelfAssessment.valueOf(e.selfAssessment()),
                        e.values(),
                        e.comment(),
                        e.documents() == null
                                ? List.of()
                                : e.documents().stream()
                                        .map(ApprovalJson::document)
                                        .toList()))
                .toList();
    }

    static String findings(List<ElementFinding> findings) {
        return write(findings.stream()
                .map(f -> new FindingJson(f.code(), f.result().name(), f.comment()))
                .toList());
    }

    static List<ElementFinding> findings(String json) {
        return read(json, new TypeReference<List<FindingJson>>() {}).stream()
                .map(f -> new ElementFinding(f.code(), FindingResult.valueOf(f.result()), f.comment()))
                .toList();
    }

    static String strings(List<String> values) {
        return write(values);
    }

    static List<String> strings(String json) {
        return read(json, new TypeReference<List<String>>() {});
    }

    static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    static <T> T read(String json, TypeReference<T> type) {
        return MAPPER.readValue(json == null || json.isBlank() ? "[]" : json, type);
    }

    private static DocumentJson document(DocumentRef d) {
        return new DocumentJson(
                d.documentId(), d.fileName(), d.mediaType(), d.size(), d.sha256(), d.uploadedAt(), d.uploadedBy());
    }

    private static DocumentRef document(DocumentJson d) {
        return new DocumentRef(
                d.documentId(), d.fileName(), d.mediaType(), d.size(), d.sha256(), d.uploadedAt(), d.uploadedBy());
    }
}
