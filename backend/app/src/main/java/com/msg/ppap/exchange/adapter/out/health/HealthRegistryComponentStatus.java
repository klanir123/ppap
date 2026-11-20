package com.msg.ppap.exchange.adapter.out.health;

import com.msg.ppap.exchange.application.port.out.ComponentStatusProbe;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.stereotype.Component;

/** {@link ComponentStatusProbe} backed by the Actuator health indicators registered by the modules. */
@Component
class HealthRegistryComponentStatus implements ComponentStatusProbe {

    private final HealthContributorRegistry registry;

    HealthRegistryComponentStatus(HealthContributorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String status(String component) {
        HealthContributor contributor = registry.getContributor(component);
        if (contributor instanceof HealthIndicator indicator) {
            try {
                return indicator.health().getStatus().getCode();
            } catch (RuntimeException ex) {
                return "DOWN";
            }
        }
        return null;
    }
}
