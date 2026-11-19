package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Decision on a revision (immutable once made).
 *
 * @param seal canonical {@code PpapDecision} with hash and signature ({@code NOT_SIGNED} for legacy imports)
 */
public record Decision(
        int revision,
        DecisionResult result,
        List<String> conditions,
        LocalDate conditionDueDate,
        String comment,
        List<ElementFinding> elementFindings,
        Instant decidedAt,
        String decidedBy,
        Seal seal) {

    public Decision {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(decidedBy, "decidedBy");
        // seal is null only transiently while the decision is being sealed
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        elementFindings = elementFindings == null ? List.of() : List.copyOf(elementFindings);
        comment = comment == null || comment.isBlank() ? null : comment;
    }
}
