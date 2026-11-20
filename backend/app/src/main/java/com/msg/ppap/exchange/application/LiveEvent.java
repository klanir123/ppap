package com.msg.ppap.exchange.application;

import java.util.Map;
import java.util.Objects;

/**
 * Event of the live stream {@code GET /api/v1/events}: {@code case.changed}, {@code exchange.changed}, {@code
 * connector.log} or {@code heartbeat}.
 *
 * @param tenantId tenant the event belongs to (only delivered to subscribers of this tenant)
 * @param type event name
 * @param data JSON object
 */
public record LiveEvent(String tenantId, String type, Map<String, Object> data) {

    public static final String CASE_CHANGED = "case.changed";
    public static final String EXCHANGE_CHANGED = "exchange.changed";
    public static final String HEARTBEAT = "heartbeat";

    /** Phase 6: live log of the connector cockpit. */
    public static final String CONNECTOR_LOG = "connector.log";

    public LiveEvent {
        Objects.requireNonNull(type, "type");
        data = data == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(data));
    }
}
