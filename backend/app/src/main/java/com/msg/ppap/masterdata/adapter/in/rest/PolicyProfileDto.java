package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.domain.PolicyProfile;
import com.msg.ppap.masterdata.domain.PolicyProfileValues;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Policy profile of relations (UC-10, docs/api/cascade-transparency-api.md §3). */
public record PolicyProfileDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PolicyAccessDto access,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PolicyUsageDto usage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        Instant createdAt,
        Instant updatedAt,

        @Schema(description = "Effect on the offers of the own EDC (write responses only, otherwise null)")
        ReconciliationDto reconciliation) {

    /** Access policy part. */
    public record PolicyAccessDto(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "BPN_GROUP: shared assets for the partner group; BPN: explicit partner BPNLs")
            PolicyProfileValues.AccessType type,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Access additionally requires Membership eq active")
            boolean membershipRequired) {}

    /** Usage policy part. */
    public record PolicyUsageDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "DataExchangeGovernance:1.0")
            String frameworkAgreement,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"cx.quality.base:1\"]")
            List<String> usagePurposes,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<PolicyConstraintDto> additionalConstraints) {}

    /** Additional atomic usage constraint (ODRL). */
    public record PolicyConstraintDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String leftOperand,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {
                        "eq",
                        "neq",
                        "gt",
                        "gteq",
                        "lt",
                        "lteq",
                        "isAnyOf",
                        "isAllOf",
                        "isNoneOf",
                        "isPartOf"
                    })
            String operator,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "String or list of strings",
                    oneOf = {String.class, String[].class})
            Object rightOperand) {}

    static PolicyProfileDto from(PolicyProfile profile) {
        return from(profile, null);
    }

    static PolicyProfileDto from(PolicyProfile profile, ReconciliationDto reconciliation) {
        var v = profile.values();
        var m = profile.metadata();
        return new PolicyProfileDto(
                profile.id(),
                v.code(),
                v.name(),
                v.description(),
                new PolicyAccessDto(v.accessType(), v.membershipRequired()),
                new PolicyUsageDto(
                        v.frameworkAgreement(),
                        v.usagePurposes(),
                        v.additionalConstraints().stream()
                                .map(c -> new PolicyConstraintDto(c.leftOperand(), c.operator(), c.rightOperand()))
                                .toList()),
                profile.active(),
                m.version(),
                m.createdAt(),
                m.updatedAt(),
                reconciliation);
    }
}
