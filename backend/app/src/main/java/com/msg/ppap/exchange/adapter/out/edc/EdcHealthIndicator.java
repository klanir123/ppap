package com.msg.ppap.exchange.adapter.out.edc;

import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator {@code edc}. Not part of the liveness or readiness groups: the application must keep
 * running (and report the problem) while the EDC is unavailable.
 */
@Component("edcHealthIndicator")
public class EdcHealthIndicator implements HealthIndicator {

    private final DataspaceConnector connector;

    public EdcHealthIndicator(DataspaceConnector connector) {
        this.connector = connector;
    }

    @Override
    public Health health() {
        ConnectorHealth result = connector.health();
        Health.Builder builder = result.reachable() ? Health.up() : Health.down();
        builder.withDetail("endpoint", result.endpoint())
                .withDetail("responseTimeMs", result.responseTime().toMillis());
        if (result.error() != null) {
            builder.withDetail("error", result.error());
        }
        return builder.build();
    }
}
