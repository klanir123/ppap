package com.msg.ppap.exchange.application.port.out;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Result of a dataspace connector health check.
 *
 * @param reachable whether the connector's management API answered successfully
 * @param endpoint checked endpoint (for diagnostics, never contains credentials)
 * @param responseTime time until the answer or failure
 * @param error failure description if not reachable
 */
public record ConnectorHealth(
        boolean reachable,
        String endpoint,
        Duration responseTime,
        @Nullable String error) {

    public ConnectorHealth {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(responseTime, "responseTime");
    }

    /** Healthy connector. */
    public static ConnectorHealth up(String endpoint, Duration responseTime) {
        return new ConnectorHealth(true, endpoint, responseTime, null);
    }

    /** Unhealthy or unreachable connector. */
    public static ConnectorHealth down(String endpoint, Duration responseTime, String error) {
        return new ConnectorHealth(false, endpoint, responseTime, error);
    }
}
