package com.msg.ppap.masterdata.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** Page of {@link PartMappingSearchDto}. */
public record PartMappingSearchPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartMappingSearchDto> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    static PartMappingSearchPage of(Page<PartMappingSearchDto> page) {
        return new PartMappingSearchPage(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
