package com.msg.ppap.masterdata.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** Page of {@link PartnerDto}. */
public record PartnerPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PartnerDto> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    static PartnerPage of(Page<PartnerDto> page) {
        return new PartnerPage(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
