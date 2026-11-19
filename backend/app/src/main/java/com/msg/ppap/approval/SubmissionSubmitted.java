package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when the supplier submits a revision (IN_PROGRESS → SUBMITTED). The exchange module provides the stored
 * canonical {@code PpapSubmission} with its JWS according to the exchange mode (push, notify + pull, pull).
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
 * @param revision submitted revision
 * @param payloadHash {@code sha256:<hex>} of the canonical payload
 * @param exchangeMode effective exchange mode ({@code PUSH}, {@code PUSH_NOTIFY_PULL_PAYLOAD}, {@code PULL}) of a
 *     dataspace-linked supplier case, {@code null} otherwise (phase 5)
 */
public record SubmissionSubmitted(
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
        String payloadHash,
        String exchangeMode)
        implements ApprovalEvent {

    @Override
    public String action() {
        return "SUBMIT";
    }

    @Override
    public Map<String, Object> details() {
        var details = baseDetails();
        ApprovalEvent.put(details, "revision", revision);
        ApprovalEvent.put(details, "payloadHash", payloadHash);
        ApprovalEvent.put(details, "exchangeMode", exchangeMode);
        return details;
    }
}
