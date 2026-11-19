package com.msg.ppap.documents.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * Immutable metadata of an uploaded document. The content lives in the object storage under the content-addressed
 * key {@code <tenant>/<sha256>}: identical content is stored once, each upload still gets its own metadata.
 */
@AggregateRoot
public final class Document {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    @Identity
    private final UUID id;

    private final String tenantId;
    private final String fileName;
    private final String mediaType;
    private final long size;
    private final String sha256;
    private final Instant uploadedAt;
    private final String uploadedBy;

    public Document(
            UUID id,
            String tenantId,
            String fileName,
            String mediaType,
            long size,
            String sha256,
            Instant uploadedAt,
            String uploadedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0");
        }
        this.size = size;
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lower-case hex characters");
        }
        this.sha256 = sha256;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt");
        this.uploadedBy = Objects.requireNonNull(uploadedBy, "uploadedBy");
    }

    /** Content-addressed object key {@code <tenant>/<sha256>}. */
    public static String storageKey(String tenantId, String sha256) {
        return tenantId + "/" + sha256;
    }

    public String storageKey() {
        return storageKey(tenantId, sha256);
    }

    public UUID id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String fileName() {
        return fileName;
    }

    public String mediaType() {
        return mediaType;
    }

    public long size() {
        return size;
    }

    public String sha256() {
        return sha256;
    }

    public Instant uploadedAt() {
        return uploadedAt;
    }

    public String uploadedBy() {
        return uploadedBy;
    }
}
