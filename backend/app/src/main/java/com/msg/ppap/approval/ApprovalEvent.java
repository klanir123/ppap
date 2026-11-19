package com.msg.ppap.approval;

import com.msg.ppap.platform.audit.AuditableEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Domain events of approval cases (public API of the module). Recorded by the audit module; the transition events
 * ({@link CaseRequested}, {@link CaseCancelled}, {@link WorkStarted}, {@link SubmissionSubmitted}, {@link
 * ReviewStarted}, {@link DecisionMade}) are the triggers of the dataspace exchange in phase 4.
 */
public interface ApprovalEvent extends AuditableEvent {

    /** Object type of all approval events. */
    String OBJECT_TYPE = "APPROVAL_CASE";

    UUID caseId();

    String caseNumber();

    UUID exchangeId();

    String ownRole();

    String source();

    String partnerBpnl();

    String fromStatus();

    String toStatus();

    @Override
    default String objectType() {
        return OBJECT_TYPE;
    }

    @Override
    default String objectId() {
        return caseId().toString();
    }

    /** Details shared by all approval events. */
    default Map<String, Object> baseDetails() {
        var details = new LinkedHashMap<String, Object>();
        details.put("businessKey", caseNumber());
        details.put("exchangeId", exchangeId().toString());
        details.put("ownRole", ownRole());
        details.put("source", source());
        details.put("partnerBpnl", partnerBpnl());
        put(details, "fromStatus", fromStatus());
        put(details, "toStatus", toStatus());
        return details;
    }

    /** Adds a value if not {@code null}. */
    static void put(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }
}
