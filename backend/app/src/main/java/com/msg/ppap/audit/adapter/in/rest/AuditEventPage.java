package com.msg.ppap.audit.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** Page of {@link AuditEventDto}. */
public record AuditEventPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuditEventDto> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    static AuditEventPage of(Page<AuditEventDto> page) {
        return new AuditEventPage(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
