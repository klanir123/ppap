package com.msg.ppap.documents.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;

/**
 * Content of a stored object. Callers must close it to release the underlying connection.
 *
 * @param content content stream
 * @param contentLength length in bytes
 * @param contentType media type, if known
 */
public record StoredDocument(
        InputStream content, long contentLength, @Nullable String contentType) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
