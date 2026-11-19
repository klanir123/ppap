package com.msg.ppap.documents.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a stored document.
 *
 * @param id technical id
 * @param fileName original (sanitized) file name
 * @param mediaType media type detected from the content (magic bytes)
 * @param size size in bytes
 * @param sha256 lower-case hex SHA-256 of the content
 * @param uploadedAt upload time
 * @param uploadedBy uploading actor
 */
public record DocumentInfo(
        UUID id, String fileName, String mediaType, long size, String sha256, Instant uploadedAt, String uploadedBy) {}
