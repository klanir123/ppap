package com.msg.ppap.documents.api;

import java.io.IOException;
import java.io.InputStream;

/**
 * Content of a stored document. Callers must close it to release the storage connection.
 *
 * @param contentLength length in bytes
 */
public record DocumentContent(DocumentInfo info, InputStream content, long contentLength) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
