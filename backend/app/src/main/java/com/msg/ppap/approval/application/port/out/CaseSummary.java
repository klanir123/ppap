package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Clarification;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.PartnerRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read model of a case for lists. */
public record CaseSummary(
        UUID id,
        String caseNumber,
        UUID exchangeId,
        CaseRole ownRole,
        CaseSource source,
        CaseStatus status,
        PartnerRef partner,
        UUID relationId,
        PartRef part,
        String partnerPartNumber,
        CatalogRef reason,
        CatalogRef level,
        LocalDate dueDate,
        String remarks,
        Integer currentRevision,
        Instant requestedAt,
        Instant submittedAt,
        Instant decidedAt,
        DecisionResult lastDecision,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Clarification clarification) {

    public String customerPartNumber() {
        return ownRole == CaseRole.CUSTOMER ? part.partNumber() : partnerPartNumber;
    }

    public String supplierPartNumber() {
        return ownRole == CaseRole.SUPPLIER ? part.partNumber() : partnerPartNumber;
    }

    public boolean overdue(LocalDate today) {
        return status.isOpen() && dueDate.isBefore(today);
    }
}
