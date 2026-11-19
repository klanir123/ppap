package com.msg.ppap.approval.domain;

import java.time.Instant;

/** Technical metadata of a persisted case; {@code version} is the optimistic lock exposed as ETag. */
public record CaseMetadata(long version, Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {}
