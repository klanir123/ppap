package com.msg.ppap.masterdata.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** Page of {@link RelationDto}. */
public record RelationPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RelationDto> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    static RelationPage of(Page<RelationDto> page) {
        return new RelationPage(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
