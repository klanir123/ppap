package com.msg.ppap.exchange.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Streamed response of a data plane {@code GET} (large documents): the caller reads and closes the body.
 *
 * @param contentLength announced length, {@code -1} if unknown
 */
public record DataPlaneStream(int status, InputStream body, long contentLength, Map<String, String> headers)
        implements AutoCloseable {

    public DataPlaneStream {
        var normalized = new TreeMap<String, String>();
        if (headers != null) {
            headers.forEach((k, v) -> normalized.put(k.toLowerCase(Locale.ROOT), v));
        }
        headers = java.util.Collections.unmodifiableMap(normalized);
    }

    public boolean successful() {
        return status >= 200 && status < 300;
    }

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
