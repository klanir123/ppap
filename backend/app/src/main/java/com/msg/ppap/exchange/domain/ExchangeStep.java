package com.msg.ppap.exchange.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A recorded step of an exchange.
 *
 * @param attempt delivery attempt (outbound) or processing run (inbound), starting at 1
 * @param durationMs duration of the step
 * @param details EDC ids, HTTP status, error … (JSON values)
 */
public record ExchangeStep(
        int attempt, StepName step, StepStatus status, Instant at, long durationMs, Map<String, Object> details) {

    public ExchangeStep {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(at, "at");
        details = details == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
