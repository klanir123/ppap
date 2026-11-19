package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a draft or request is cancelled. Phase 4: withdrawal notification to the supplier for sent requests.
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
 * @param reason cancellation reason
 */
public record CaseCancelled(
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
        String reason)
        implements ApprovalEvent {

    @Override
    public String action() {
        return "CANCEL";
    }

    @Override
    public Map<String, Object> details() {
        var details = baseDetails();
        ApprovalEvent.put(details, "reason", reason);
        return details;
    }
}
