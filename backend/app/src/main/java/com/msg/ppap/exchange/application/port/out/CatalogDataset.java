package com.msg.ppap.exchange.application.port.out;

import java.util.List;
import java.util.Map;

/**
 * Dataset of a partner's catalog (connector cockpit, UC-10).
 *
 * @param assetId asset id
 * @param type taxonomy type ({@code dct:type}), expanded, {@code null} if absent
 * @param properties remaining dataset properties (compacted JSON-LD)
 * @param offers ODRL offers (compacted JSON-LD)
 */
public record CatalogDataset(
        String assetId, String type, Map<String, Object> properties, List<Map<String, Object>> offers) {

    public CatalogDataset {
        properties = properties == null ? Map.of() : java.util.Collections.unmodifiableMap(properties);
        offers = offers == null ? List.of() : List.copyOf(offers);
    }
}
