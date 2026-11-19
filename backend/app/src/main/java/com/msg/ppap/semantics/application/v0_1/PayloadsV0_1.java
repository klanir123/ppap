package com.msg.ppap.semantics.application.v0_1;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Payload structure of exchange format v0.1 (SAMM models {@code urn:samm:com.msg.ppap:0.1.0}, generated JSON Schemas).
 * Structured values and value schemas are JSON text (SAMM has no type for arbitrary JSON). Optional properties are
 * omitted when absent, so the canonical form never contains {@code null} members.
 */
final class PayloadsV0_1 {

    private PayloadsV0_1() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Submission(
            SubmissionHeader header,
            List<Element> elements,
            List<SubSupplierReference> subSupplierReferences,
            String submittedAt,
            String submittedBy) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SubmissionHeader(
            String exchangeId,
            String caseNumber,
            int revision,
            String customerBpnl,
            String supplierBpnl,
            String customerPartNumber,
            String supplierPartNumber,
            String reasonCode,
            String levelCode) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Element(
            String code,
            String requirement,
            String selfAssessment,
            String values,
            String comment,
            List<Document> documents) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Document(String fileName, String mediaType, long size, String sha256) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SubSupplierReference(
            String subSupplierBpnl, String partNumber, String exchangeId, int revision, String result, String hash) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Decision(
            String exchangeId,
            String caseNumber,
            int revision,
            String submissionHash,
            String result,
            List<Condition> conditions,
            String conditionDueDate,
            String comment,
            List<Finding> elementFindings,
            String decidedAt,
            String decidedBy) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Condition(String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Finding(String code, String result, String comment) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Request(
            RequestHeader header,
            CatalogEntry reason,
            CatalogEntry level,
            String dueDate,
            String remarks,
            String requestedAt,
            String requestedBy,
            List<RequestedElement> elements,
            List<Document> attachments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RequestHeader(
            String exchangeId,
            String caseNumber,
            String customerBpnl,
            String supplierBpnl,
            String customerPartNumber,
            String supplierPartNumber,
            String partName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CatalogEntry(String code, String nameDe, String nameEn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RequestedElement(String code, String requirement, String note, Snapshot snapshot) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Snapshot(String nameDe, String nameEn, String evidenceType, String valueSchema) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatusUpdate(String exchangeId, String status, int revision, String at, String by) {}

    record SubmissionAvailable(
            String exchangeId, int revision, String payloadHash, String shellId, String submodelId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Withdrawal(String exchangeId, String reason, String withdrawnAt, String withdrawnBy) {}
}
