package com.msg.ppap.approval.domain;

/** Overall decision. */
public enum DecisionResult {
    APPROVED,
    CONDITIONALLY_APPROVED,
    REJECTED;

    /** Case status after the decision. */
    public CaseStatus status() {
        return CaseStatus.valueOf(name());
    }
}
