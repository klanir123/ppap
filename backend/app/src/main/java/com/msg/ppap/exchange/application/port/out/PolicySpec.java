package com.msg.ppap.exchange.application.port.out;

import java.util.List;
import java.util.Objects;

/**
 * ODRL policy (CX-0152 2025/9) with a single permission whose constraints are combined with {@code and}.
 *
 * @param id deterministic policy id
 * @param action {@code access} (access policy) or {@code use} (usage policy)
 * @param constraints atomic constraints
 */
public record PolicySpec(String id, String action, List<Constraint> constraints) {

    public PolicySpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        constraints = List.copyOf(constraints);
    }

    /**
     * Atomic constraint.
     *
     * @param rightOperand string or list of strings
     */
    public record Constraint(String leftOperand, String operator, Object rightOperand) {}
}
