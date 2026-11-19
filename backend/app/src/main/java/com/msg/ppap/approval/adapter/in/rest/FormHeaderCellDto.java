package com.msg.ppap.approval.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/** Header value with its cell. */
public record FormHeaderCellDto(
        String value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cell) {}
