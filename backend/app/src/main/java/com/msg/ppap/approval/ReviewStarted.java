package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when the customer starts the review (SUBMITTED → IN_REVIEW). Phase 4: optional status update to the supplier.
 *
 * @param tenantId tenant BPNL
 * @param occurredAt time of the action
 * @param actor acting user or system
 * @param caseId case id
 * @param caseNumber tenant-local case number
 * @param exchangeId exchange id shared with the partner's case
 * @param ownRole own role in the case ({@code CUSTOMER}, {@code SUPPLIER})
 * @param source origin of the case
 * @param partnerBpnl partner BPNL
 * @param fromStatus status before
 * @param toStatus status after
 * @param revision reviewed revision
 */
public record ReviewStarted(
        String tenantId,
        Instant occurredAt,
        String actor,
        UUID caseId,
        String caseNumber,
        UUID exchangeId,
        String ownRole,
        String source,
        String partnerBpnl,
        String fromStatus,
        String toStatus,
        Integer revision)
        implements ApprovalEvent {

    @Override
    public String action() {
        return "START_REVIEW";
    }

    @Override
    public Map<String, Object> details() {
        var details = baseDetails();
        ApprovalEvent.put(details, "revision", revision);
        return details;
    }
}
