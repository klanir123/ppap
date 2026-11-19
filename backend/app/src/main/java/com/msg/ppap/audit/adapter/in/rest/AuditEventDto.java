package com.msg.ppap.audit.adapter.in.rest;

import com.msg.ppap.audit.domain.AuditRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Audit log entry. */
public record AuditEventDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,

        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                    "PART",
                    "PARTNER",
                    "SITE",
                    "RELATION",
                    "PART_MAPPING",
                    "CATALOG_ELEMENT",
                    "SUBMISSION_LEVEL",
                    "SUBMISSION_REASON",
                    "SEED",
                    "APPROVAL_CASE",
                    "DOCUMENT"
                })
        String objectType,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String objectId,

        @Schema(description = "Business key of the object at the time of the event, e.g. the part number")
        String businessKey,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> details,

        @Schema(description = "Hash of the previous entry of the same object")
        String prevHash,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "SHA-256 (hex) over the canonical entry")
        String hash) {

    static AuditEventDto from(AuditRecord record) {
        return new AuditEventDto(
                record.id(),
                record.occurredAt(),
                record.actor(),
                record.action(),
                record.objectType(),
                record.objectId(),
                record.details().get("businessKey") instanceof String key ? key : null,
                record.details(),
                record.prevHash(),
                record.hash());
    }
}
