package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Document reference with metadata and transfer state (phase 5: partner documents are pulled via EDC). */
public record DocumentRefDto(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = {"string", "null"},
                format = "uuid",
                description = "Local document id; null for partner documents until they are fetched and verified")
        UUID id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mediaType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sha256,
        Instant uploadedAt,
        String uploadedBy,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "LOCAL = own document; partner documents: PENDING, FETCHED, FAILED, NOT_AVAILABLE")
        TransferStatus transferStatus,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the local content matches sha256 (own uploads and verified transfers)")
        boolean hashVerified,

        @Schema(description = "Error code of a FAILED transfer (e.g. DOCUMENT_HASH_MISMATCH), otherwise null")
        String transferError) {

    /** Transfer state of a referenced document (docs/api/pull-api.md). */
    public enum TransferStatus {
        LOCAL,
        PENDING,
        FETCHED,
        FAILED,
        NOT_AVAILABLE
    }
}
