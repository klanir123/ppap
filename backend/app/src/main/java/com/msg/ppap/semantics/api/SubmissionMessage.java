package com.msg.ppap.semantics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Submission of a revision (supplier → customer), exchange model {@code PpapSubmission}.
 *
 * @param exchangeId exchange id of the request
 * @param caseNumber case number of the sender (supplier)
 * @param revision revision number (1, 2, …)
 * @param customerBpnl customer BPNL
 * @param supplierBpnl supplier BPNL
 * @param customerPartNumber customer part number
 * @param supplierPartNumber supplier part number
 * @param reasonCode submission reason code
 * @param levelCode submission level code
 * @param elements element evidence
 * @param subSupplierReferences references to decided approvals of sub-suppliers (UC-09)
 * @param submittedAt submission time
 * @param submittedBy submitting user (confirmation)
 */
public record SubmissionMessage(
        UUID exchangeId,
        String caseNumber,
        int revision,
        String customerBpnl,
        String supplierBpnl,
        String customerPartNumber,
        String supplierPartNumber,
        String reasonCode,
        String levelCode,
        List<Element> elements,
        List<SubSupplierReference> subSupplierReferences,
        Instant submittedAt,
        String submittedBy) {

    public SubmissionMessage {
        elements = elements == null ? List.of() : List.copyOf(elements);
        subSupplierReferences = subSupplierReferences == null ? List.of() : List.copyOf(subSupplierReferences);
    }

    /**
     * Evidence of one element.
     *
     * @param values structured values (JSON value tree: map, list, scalar) or {@code null}
     */
    public record Element(
            String code,
            String requirement,
            String selfAssessment,
            Object values,
            String comment,
            List<DocumentReference> documents) {

        public Element {
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }

    /**
     * Reference to the decided approval of a sub-supplier (UC-09): metadata only, never documents.
     *
     * @param subSupplierBpnl BPNL of the sub-supplier (plain text, TODO(EG) pseudonymisation, ASSUMPTIONS A-18)
     * @param subSupplierName name of the sub-supplier (master data of the submitting supplier), optional
     * @param exchangeId exchange id of the sub-supplier case
     * @param caseNumber case number of the sub-supplier case at the submitting supplier
     * @param partNumber sub-part number at the submitting supplier
     * @param partName sub-part name, optional
     * @param supplierPartNumber part number at the sub-supplier, optional
     * @param revision decided revision
     * @param decisionResult {@code APPROVED} or {@code CONDITIONALLY_APPROVED}
     * @param decidedAt time of the decision
     * @param decisionHash {@code payloadHash} of the signed decision
     * @param conditionDueDate due date of the conditions, optional
     */
    public record SubSupplierReference(
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
            LocalDate conditionDueDate) {}
}
