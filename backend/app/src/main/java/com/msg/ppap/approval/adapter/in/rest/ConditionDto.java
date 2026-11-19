package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Condition of a conditional approval. */
public record ConditionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text) {}
