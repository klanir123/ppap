package com.msg.ppap.exchange.application.port.out;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Response of a data plane call (headers as forwarded by the provider data plane). */
public record DataPlaneResponse(int status, byte[] body, Map<String, String> headers) {

    public DataPlaneResponse {
        body = body == null ? new byte[0] : body.clone();
        var normalized = new TreeMap<String, String>();
        if (headers != null) {
            headers.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
        }
        headers = java.util.Collections.unmodifiableMap(normalized);
    }

    public DataPlaneResponse(int status, byte[] body) {
        this(status, body, Map.of());
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public boolean successful() {
        return status >= 200 && status < 300;
    }

    /** Header value (case-insensitive), {@code null} if absent. */
    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /** Body as text, shortened for diagnostics. */
    public String bodySnippet() {
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
