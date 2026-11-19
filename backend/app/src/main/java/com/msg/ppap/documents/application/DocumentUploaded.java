package com.msg.ppap.documents.application;

import com.msg.ppap.platform.audit.AuditableEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Audit event of a document upload. */
public record DocumentUploaded(
        String tenantId,
        Instant occurredAt,
        String actor,
        UUID documentId,
        String fileName,
        String mediaType,
        long size,
        String sha256)
        implements AuditableEvent {

    @Override
    public String action() {
        return "UPLOADED";
    }

    @Override
    public String objectType() {
        return DocumentService.OBJECT_TYPE;
    }

    @Override
    public String objectId() {
        return documentId.toString();
    }

    @Override
    public Map<String, Object> details() {
        var details = new LinkedHashMap<String, Object>();
        details.put("fileName", fileName);
        details.put("mediaType", mediaType);
        details.put("size", size);
        details.put("sha256", sha256);
        return details;
    }
}
