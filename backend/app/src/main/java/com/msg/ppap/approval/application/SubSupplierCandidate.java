package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.SubSupplierReference;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A decided child case (sub-supplier request) of a supplier case (UC-09). Only approvals can be referenced; rejected
 * children are listed for information.
 *
 * @param partNumber own sub-part number
 * @param supplierPartNumber part number at the sub-supplier
 * @param decisionHash {@code payloadHash} of the signed decision (made by the own tenant as customer)
 */
public record SubSupplierCandidate(
        UUID caseId,
        String caseNumber,
        PartnerRef subSupplier,
        String partNumber,
        String partName,
        String supplierPartNumber,
        CaseStatus status,
        int revision,
        DecisionResult decisionResult,
        Instant decidedAt,
        String decisionHash,
        LocalDate conditionDueDate,
        UUID exchangeId) {

    /** Whether the decision can be referenced (approved or conditionally approved). */
    public boolean referenceable() {
        return SubSupplierReference.referenceable(decisionResult) && decisionHash != null && partNumber != null;
    }

    /** The reference to put into the own submission. */
    public SubSupplierReference reference() {
        return new SubSupplierReference(
                caseId,
                subSupplier.bpnl(),
                subSupplier.name(),
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
}
