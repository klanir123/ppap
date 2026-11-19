package com.msg.ppap.approval.domain;

import com.msg.ppap.platform.domain.FieldViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Mandatory justifications of decisions (AK-07-1). */
public final class DecisionRules {

    private DecisionRules() {}

    /**
     * Checks a decision.
     *
     * <ul>
     *   <li>{@code REJECTED}: at least one element {@code NOT_ACCEPTED}, each with comment ({@code
     *       REJECTION_REASON_REQUIRED})
     *   <li>{@code CONDITIONALLY_APPROVED}: at least one condition and a due date not in the past ({@code
     *       CONDITIONS_REQUIRED})
     *   <li>{@code APPROVED}: no element {@code NOT_ACCEPTED} ({@code ELEMENTS_NOT_ACCEPTED})
     * </ul>
     */
    public static void check(
            DecisionResult result,
            List<String> conditions,
            LocalDate conditionDueDate,
            Collection<ElementFinding> findings,
            LocalDate today) {
        List<ElementFinding> notAccepted = findings.stream()
                .filter(f -> f.result() == FindingResult.NOT_ACCEPTED)
                .toList();
        List<FieldViolation> violations = new ArrayList<>();
        switch (result) {
            case REJECTED -> {
                if (notAccepted.isEmpty()) {
                    violations.add(new FieldViolation(
                            "elementFindings", ApprovalErrors.REJECTION_REASON_REQUIRED, "no element NOT_ACCEPTED"));
                }
                notAccepted.stream()
                        .filter(f -> f.comment() == null)
                        .forEach(f -> violations.add(new FieldViolation(
                                "elementFindings." + f.code() + ".comment",
                                ApprovalErrors.REJECTION_REASON_REQUIRED,
                                "reason required for rejected element")));
                throwIfAny(ApprovalErrors.REJECTION_REASON_REQUIRED, violations);
            }
            case CONDITIONALLY_APPROVED -> {
                if (conditions.stream().noneMatch(c -> c != null && !c.isBlank())) {
                    violations.add(new FieldViolation(
                            "conditions", ApprovalErrors.CONDITIONS_REQUIRED, "at least one condition required"));
                }
                if (conditionDueDate == null) {
                    violations.add(new FieldViolation(
                            "conditionDueDate", ApprovalErrors.CONDITIONS_REQUIRED, "due date required"));
                } else if (conditionDueDate.isBefore(today)) {
                    violations.add(new FieldViolation(
                            "conditionDueDate",
                            ApprovalErrors.CONDITIONS_REQUIRED,
                            "due date must not be in the past"));
                }
                throwIfAny(ApprovalErrors.CONDITIONS_REQUIRED, violations);
            }
            case APPROVED -> {
                notAccepted.forEach(f -> violations.add(new FieldViolation(
                        "elementFindings." + f.code(), ApprovalErrors.ELEMENTS_NOT_ACCEPTED, "element NOT_ACCEPTED")));
                throwIfAny(ApprovalErrors.ELEMENTS_NOT_ACCEPTED, violations);
            }
        }
    }

    private static void throwIfAny(String code, List<FieldViolation> violations) {
        if (!violations.isEmpty()) {
            throw ApprovalErrors.rule(code, violations);
        }
    }
}
