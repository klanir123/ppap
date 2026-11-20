package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Reference of a revision to the decided approval of a sub-supplier (UC-09, cascade): metadata of an own child case
 * (sub-supplier request) – never documents or inspection results of the sub-supplier. Part of the signed {@code
 * PpapSubmission}.
 *
 * @param childCaseId own child case (supplier side); {@code null} on the receiving customer side
 * @param subSupplierBpnl BPNL of the sub-supplier (plain text, TODO(EG) pseudonymisation, ASSUMPTIONS A-18)
 * @param subSupplierName name of the sub-supplier, optional
 * @param exchangeId exchange id of the child case
 * @param caseNumber case number of the child case at the submitting supplier
 * @param partNumber sub-part number of the submitting supplier
 * @param partName sub-part name, optional
 * @param supplierPartNumber part number at the sub-supplier, optional
 * @param revision decided revision of the child case
 * @param decisionResult {@code APPROVED} or {@code CONDITIONALLY_APPROVED}
 * @param decidedAt time of the decision
 * @param decisionHash {@code payloadHash} of the signed decision
 * @param conditionDueDate due date of conditions, optional
 */
public record SubSupplierReference(
        UUID childCaseId,
        String subSupplierBpnl,
        String subSupplierName,
        UUID exchangeId,
        String caseNumber,
        String partNumber,
        String partName,
        String supplierPartNumber,
        int revision,
        DecisionResult decisionResult,
        Instant decidedAt,
        String decisionHash,
        LocalDate conditionDueDate) {

    public SubSupplierReference {
        Objects.requireNonNull(subSupplierBpnl, "subSupplierBpnl");
        Objects.requireNonNull(exchangeId, "exchangeId");
        Objects.requireNonNull(caseNumber, "caseNumber");
        Objects.requireNonNull(partNumber, "partNumber");
        Objects.requireNonNull(decisionResult, "decisionResult");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(decisionHash, "decisionHash");
        if (!referenceable(decisionResult)) {
            throw new IllegalArgumentException("only approvals can be referenced, not " + decisionResult);
        }
        subSupplierName = blankToNull(subSupplierName);
        partName = blankToNull(partName);
        supplierPartNumber = blankToNull(supplierPartNumber);
    }

    /** Whether a decision with this result may be referenced (AK-09-1: approvals only). */
    public static boolean referenceable(DecisionResult result) {
        return result == DecisionResult.APPROVED || result == DecisionResult.CONDITIONALLY_APPROVED;
    }

    /** Copy without the local child case id (as received by the customer). */
    public SubSupplierReference withoutChildCase() {
        return new SubSupplierReference(
                null,
                subSupplierBpnl,
                subSupplierName,
                exchangeId,
                caseNumber,
                partNumber,
                partName,
                supplierPartNumber,
                revision,
                decisionResult,
                decidedAt,
                decisionHash,
                conditionDueDate);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
