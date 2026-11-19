package com.msg.ppap.semantics.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Decision on a revision (customer → supplier), exchange model {@code PpapDecision}.
 *
 * @param exchangeId exchange id
 * @param caseNumber case number of the sender (customer)
 * @param revision decided revision
 * @param submissionHash payload hash of the decided submission
 * @param result {@code APPROVED}, {@code CONDITIONALLY_APPROVED} or {@code REJECTED}
 * @param conditions conditions (conditional approval)
 * @param conditionDueDate due date of the conditions
 * @param comment overall comment
 * @param elementFindings findings per element
 * @param decidedAt decision time
 * @param decidedBy deciding user (confirmation)
 */
public record DecisionMessage(
        UUID exchangeId,
        String caseNumber,
        int revision,
        String submissionHash,
        String result,
        List<String> conditions,
        LocalDate conditionDueDate,
        String comment,
        List<Finding> elementFindings,
        Instant decidedAt,
        String decidedBy) {

    public DecisionMessage {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        elementFindings = elementFindings == null ? List.of() : List.copyOf(elementFindings);
    }

    /** Finding on one element. */
    public record Finding(String code, String result, String comment) {}
}
