package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.FindingResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** Review result of an element. */
public record ElementFindingRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ACCEPTED, NOT_ACCEPTED or ACCEPTED_WITH_CONDITION")
        FindingResult result,

        String comment) {}
