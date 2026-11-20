package com.msg.ppap.usage.domain;

/**
 * Usage metrics (UC-12, ASSUMPTIONS A-30): an approval case counts once in the month of its first submission
 * (revision 1) – at the submitting supplier and at the receiving customer; further revisions are counted separately.
 */
public enum UsageMetric {
    APPROVAL_CASES_SUBMITTED,
    REVISIONS_SUBMITTED,
    APPROVAL_CASES_RECEIVED,
    REVISIONS_RECEIVED;

    /** Whether the metric counts approval cases (revision 1). */
    public boolean approvalCase() {
        return this == APPROVAL_CASES_SUBMITTED || this == APPROVAL_CASES_RECEIVED;
    }
}
