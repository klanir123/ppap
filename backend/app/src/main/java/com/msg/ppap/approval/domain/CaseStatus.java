package com.msg.ppap.approval.domain;

import java.util.EnumSet;
import java.util.Set;

/** Status of an approval case (identical on both sides). */
public enum CaseStatus {
    DRAFT,
    REQUESTED,
    IN_PROGRESS,
    SUBMITTED,
    IN_REVIEW,
    APPROVED,
    CONDITIONALLY_APPROVED,
    REJECTED,
    CANCELLED;

    /** Statuses that count as open (sent and not finally decided): basis for "open" and "overdue". */
    public static final Set<CaseStatus> OPEN = EnumSet.of(REQUESTED, IN_PROGRESS, SUBMITTED, IN_REVIEW, REJECTED);

    /** Decision outcomes. */
    public static final Set<CaseStatus> DECIDED = EnumSet.of(APPROVED, CONDITIONALLY_APPROVED, REJECTED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
