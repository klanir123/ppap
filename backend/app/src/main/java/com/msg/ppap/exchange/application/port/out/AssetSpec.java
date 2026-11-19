package com.msg.ppap.exchange.application.port.out;

import java.util.Map;
import java.util.Objects;

/**
 * Asset to provision.
 *
 * @param id deterministic asset id
 * @param type taxonomy type ({@code dct:type}), e.g. {@code cx-taxo:PpapNotificationAPI}
 * @param version {@code cx-common:version}
 * @param description human readable description
 * @param dataAddress HttpData address properties ({@code baseUrl}, {@code proxyMethod}, {@code authKey}, …)
 */
public record AssetSpec(String id, String type, String version, String description, Map<String, String> dataAddress) {

    public AssetSpec {
        Objects.requireNonNull(id, "id");
        dataAddress = Map.copyOf(dataAddress);
    }
}
