package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.domain.Requirement;
import io.swagger.v3.oas.annotations.media.Schema;

/** Requested element. */
public record RequestedElementDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Requirement requirement,
        String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ElementSnapshotDto snapshot) {}
