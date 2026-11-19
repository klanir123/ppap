package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.Requirement;
import io.swagger.v3.oas.annotations.media.Schema;

/** Requested element (input). */
public record RequestedElementInput(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,

        @Schema(description = "REQUIRED or ON_REQUEST; default from the level")
        Requirement requirement,

        String note) {}
