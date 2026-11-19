package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published for all other actions on a case (creation, edits, review findings, new revisions, received partner
 * messages, seed import); recorded by the audit module only.
 *
 * @param action action, e.g. {@code CREATE}, {@code EDIT_SUBMISSION}, {@code RECEIVE_SUBMISSION}
 * @param revision affected revision, if any
 * @param elementCode affected element, if any
 */
public record ApprovalCaseChanged(
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
        String action,
        Integer revision,
        String elementCode)
        implements ApprovalEvent {

    @Override
    public Map<String, Object> details() {
        var details = baseDetails();
        ApprovalEvent.put(details, "revision", revision);
        ApprovalEvent.put(details, "elementCode", elementCode);
        return details;
    }
}
