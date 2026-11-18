package com.msg.ppap.masterdata.domain;

import java.time.Instant;

/**
 * Technical metadata of a persisted aggregate, maintained by the persistence adapter.
 *
 * @param version optimistic locking version (exposed as ETag)
 * @param createdAt creation time
 * @param createdBy creating actor
 * @param updatedAt last modification time
 * @param updatedBy last modifying actor
 */
public record Metadata(long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {}
