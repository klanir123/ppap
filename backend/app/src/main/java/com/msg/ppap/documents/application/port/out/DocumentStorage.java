package com.msg.ppap.documents.application.port.out;

import java.io.InputStream;
import java.util.Optional;

/** Binary storage for documents, addressed by an opaque object key. */
public interface DocumentStorage {

    /**
     * Stores content under the given key, replacing existing content.
     *
     * @throws DocumentStorageException if the storage is unavailable
     */
    void put(String key, InputStream content, long contentLength, String contentType);

    /**
     * Loads the content stored under the given key.
     *
     * @return the document, or empty if no object exists under the key
     * @throws DocumentStorageException if the storage is unavailable
     */
    Optional<StoredDocument> get(String key);
}
