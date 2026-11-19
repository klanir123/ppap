package com.msg.ppap.semantics.api;

import java.time.Instant;
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
 * @param subSupplierReferences references to approvals of sub-suppliers (UC-09; empty for now)
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

    /** Reference to the approval of a sub-supplier (UC-09). */
    public record SubSupplierReference(
            String subSupplierBpnl, String partNumber, UUID exchangeId, int revision, String result, String hash) {}
}
