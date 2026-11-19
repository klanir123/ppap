package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.FindingResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** Review finding of an element. */
public record FindingDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FindingResult result,
        String comment) {}
