package com.msg.ppap.platform.status;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the health of infrastructure components to authenticated users (system status page). Aggregates the
 * Actuator health indicators registered by the modules.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
public class SystemStatusController {

    private static final Logger LOG = LoggerFactory.getLogger(SystemStatusController.class);

    /** Component name in the response mapped to the Actuator health contributor name. */
    private static final List<Map.Entry<String, String>> COMPONENTS = List.of(
            Map.entry("database", "db"),
            Map.entry("edc", "edc"),
            Map.entry("objectStorage", "objectStorage"),
            Map.entry("dtr", "dtr"));

    private final HealthContributorRegistry registry;

    public SystemStatusController(HealthContributorRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/status")
    @Operation(operationId = "getSystemStatus", summary = "Status of database, EDC, object storage and DTR")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(COMPONENTS.stream()
                .map(component -> check(component.getKey(), component.getValue()))
                .toList());
    }

    private ComponentStatus check(String name, String contributorName) {
        HealthContributor contributor = registry.getContributor(contributorName);
        if (!(contributor instanceof HealthIndicator indicator)) {
            // not (yet) available, e.g. the DTR client arrives in phase 4
            return new ComponentStatus(name, ComponentStatus.State.UNKNOWN, Map.of());
        }
        try {
            Health health = indicator.health(true);
            return new ComponentStatus(name, map(health.getStatus()), health.getDetails());
        } catch (RuntimeException ex) {
            LOG.warn("Health check of {} failed", name, ex);
            return new ComponentStatus(
                    name,
                    ComponentStatus.State.DOWN,
                    Map.of("error", ex.getClass().getName()));
        }
    }

    private static ComponentStatus.State map(Status status) {
        if (Status.UP.equals(status)) {
            return ComponentStatus.State.UP;
        }
        if (Status.DOWN.equals(status) || Status.OUT_OF_SERVICE.equals(status)) {
            return ComponentStatus.State.DOWN;
        }
        return ComponentStatus.State.UNKNOWN;
    }
}
