package com.msg.ppap.approval.domain;

/**
 * Tenant-specific rules of the state machine.
 *
 * @param allowTechnicalDecisions technical clients ({@code SYSTEM_INTEGRATION}) may review and decide (UC-07 A2,
 *     {@code ppap.decision.allow-technical-clients})
 */
public record CasePolicy(boolean allowTechnicalDecisions) {

    /** Default: only quality engineers decide. */
    public static final CasePolicy DEFAULT = new CasePolicy(false);
}
