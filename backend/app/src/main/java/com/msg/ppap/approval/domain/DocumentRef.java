package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a document with a metadata snapshot. Local documents carry their id; documents of received payloads
 * are known by metadata and hash only until they are pulled (phase 5).
 */
public record DocumentRef(
        UUID documentId,
        String fileName,
        String mediaType,
        long size,
        String sha256,
        Instant uploadedAt,
        String uploadedBy) {

    public DocumentRef {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(sha256, "sha256");
    }
}
