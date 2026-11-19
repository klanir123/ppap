package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cancellation with reason. */
public record CancelCaseRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}
