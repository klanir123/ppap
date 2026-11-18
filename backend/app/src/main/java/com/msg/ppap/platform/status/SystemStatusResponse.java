package com.msg.ppap.platform.status;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Aggregated status of the connector's infrastructure dependencies.
 *
 * @param components status per component (database, EDC, object storage, digital twin registry)
 */
public record SystemStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ComponentStatus> components) {}
