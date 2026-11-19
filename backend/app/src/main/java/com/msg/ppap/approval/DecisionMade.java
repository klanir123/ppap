package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when the customer decides (IN_REVIEW → APPROVED | CONDITIONALLY_APPROVED | REJECTED). Phase 4: the exchange module transfers the stored canonical {@code PpapDecision} with its JWS.
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
 * @param revision decided revision
 * @param result decision result
 * @param payloadHash {@code sha256:<hex>} of the canonical decision payload
 */
public record DecisionMade(
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
        Integer revision,
        String result,
        String payloadHash)
        implements ApprovalEvent {

    @Override
    public String action() {
        return "DECIDE";
    }

    @Override
    public Map<String, Object> details() {
        var details = baseDetails();
        ApprovalEvent.put(details, "revision", revision);
        ApprovalEvent.put(details, "result", result);
        ApprovalEvent.put(details, "payloadHash", payloadHash);
        return details;
    }
}
