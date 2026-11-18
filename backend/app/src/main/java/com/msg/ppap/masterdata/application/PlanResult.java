package com.msg.ppap.masterdata.application;

import com.msg.ppap.platform.domain.FieldViolation;
import java.util.List;

/**
 * Result of a dry-run upsert (import preview).
 *
 * @param action what an upsert would do
 * @param warnings non-blocking findings
 */
public record PlanResult(ChangeAction action, List<FieldViolation> warnings) {

    public PlanResult {
        warnings = List.copyOf(warnings);
    }
}
