package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Document reference with metadata (id is null for documents of received payloads until pulled). */
public record DocumentRefDto(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                format = "uuid",
                description = "Document id; null for documents of received payloads until they are pulled (phase 5)")
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mediaType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sha256,
        Instant uploadedAt,
        String uploadedBy) {}
