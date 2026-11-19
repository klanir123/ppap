package com.msg.ppap.approval.domain;

import java.time.Instant;

/**
 * An action applied to a case, collected by the aggregate and published by the application layer as domain event.
 *
 * @param from status before ({@code null} on creation)
 * @param to status after (equal to {@code from} for edit actions)
 * @param revision affected revision, if any
 * @param elementCode affected element, if any
 */
public record CaseChange(
        CaseAction action,
        CaseStatus from,
        CaseStatus to,
        Instant at,
        String actor,
        String comment,
        Integer revision,
        String elementCode) {

    /** Whether the status changed (timeline relevant). */
    public boolean transition() {
        return from != to;
    }
}
