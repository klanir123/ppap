package com.msg.ppap.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The authenticated user.
 *
 * @param userId stable user id ({@code sub})
 * @param username login name ({@code preferred_username})
 * @param displayName full name ({@code name})
 * @param email e-mail address
 * @param roles realm roles without {@code ROLE_} prefix, sorted
 * @param tenant tenant the user belongs to
 */
public record MeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userId,
        @Nullable String username,
        @Nullable String displayName,
        @Nullable String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TenantInfo tenant) {}
