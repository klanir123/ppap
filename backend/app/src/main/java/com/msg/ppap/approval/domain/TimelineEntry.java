package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Status change of a case (immutable history).
 *
 * @param from previous status, {@code null} for the creation of a case
 */
public record TimelineEntry(
        UUID id, Instant at, String actor, CaseStatus from, CaseStatus to, CaseAction action, String comment) {

    public TimelineEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(action, "action");
        comment = comment == null || comment.isBlank() ? null : comment;
    }
}
