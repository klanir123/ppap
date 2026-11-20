package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PolicyProfileCommand;
import com.msg.ppap.masterdata.domain.PolicyProfileValues;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Create or replace a policy profile.
 *
 * @param active {@code null} keeps the current state (new profiles are active)
 */
public record PolicyProfileRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String description,

        @Schema(description = "Default BPN_GROUP, membershipRequired true")
        PolicyProfileDto.PolicyAccessDto access,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PolicyProfileDto.PolicyUsageDto usage,
        Boolean active) {

    PolicyProfileCommand toCommand() {
        return new PolicyProfileCommand(
                code,
                name,
                description,
                access == null || access.type() == null ? null : access.type().name(),
                access == null ? null : access.membershipRequired(),
                usage == null ? null : usage.frameworkAgreement(),
                usage == null ? null : usage.usagePurposes(),
                usage == null || usage.additionalConstraints() == null
                        ? List.of()
                        : usage.additionalConstraints().stream()
                                .map(c -> c == null
                                        ? null
                                        : new PolicyProfileValues.RawConstraint(
                                                c.leftOperand(), c.operator(), c.rightOperand()))
                                .toList(),
                active);
    }
}
