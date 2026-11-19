package com.msg.ppap.documents.adapter.in.rest;

import com.msg.ppap.documents.api.DocumentInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Metadata of a tenant-local document. */
public record DocumentDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Detected from the content")
        String mediaType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long size,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "SHA-256 (hex) of the content")
        String sha256,

        Instant uploadedAt,
        String uploadedBy) {

    static DocumentDto from(DocumentInfo info) {
        return new DocumentDto(
                info.id(),
                info.fileName(),
                info.mediaType(),
                info.size(),
                info.sha256(),
                info.uploadedAt(),
                info.uploadedBy());
    }
}
