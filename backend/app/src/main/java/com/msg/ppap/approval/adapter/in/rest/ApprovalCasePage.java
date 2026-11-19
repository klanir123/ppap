package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** Page of {@link ApprovalCaseDto}. */
public record ApprovalCasePage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ApprovalCaseDto> content,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {

    static ApprovalCasePage of(Page<ApprovalCaseDto> page) {
        return new ApprovalCasePage(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
