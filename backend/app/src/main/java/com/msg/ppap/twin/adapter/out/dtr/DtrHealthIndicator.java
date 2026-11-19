package com.msg.ppap.twin.adapter.out.dtr;

import com.msg.ppap.twin.application.port.out.TwinRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Health of the own DTR (component {@code dtr} of the system status); UNKNOWN without DTR. */
@Component("dtr")
class DtrHealthIndicator implements HealthIndicator {

    private final TwinRegistry registry;

    DtrHealthIndicator(TwinRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        if (!registry.configured()) {
            return Health.unknown().withDetail("configured", false).build();
        }
        return registry.reachable() ? Health.up().build() : Health.down().build();
    }
}
