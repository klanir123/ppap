package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Explicit confirmation of the submission. */
public record SubmitRevisionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean confirmation) {}
