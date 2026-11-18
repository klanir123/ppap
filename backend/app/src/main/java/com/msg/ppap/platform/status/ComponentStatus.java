package com.msg.ppap.platform.status;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Status of a single infrastructure component.
 *
 * @param name component name ({@code database}, {@code edc}, {@code objectStorage}, {@code dtr})
 * @param status health state
 * @param details component-specific diagnostic details
 */
public record ComponentStatus(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> details) {

    /** Simplified health state of a component. */
    public enum State {
        UP,
        DOWN,
        UNKNOWN
    }
}
