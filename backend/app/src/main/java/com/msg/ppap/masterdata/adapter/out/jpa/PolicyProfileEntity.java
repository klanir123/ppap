package com.msg.ppap.masterdata.adapter.out.jpa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.msg.ppap.masterdata.domain.PolicyProfile;
import com.msg.ppap.masterdata.domain.PolicyProfileValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Entity
@Table(name = "md_policy_profile")
class PolicyProfileEntity extends MasterdataEntity {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false, length = 16)
    private PolicyProfileValues.AccessType accessType;

    @Column(name = "membership_required", nullable = false)
    private boolean membershipRequired;

    @Column(name = "framework_agreement", nullable = false)
    private String frameworkAgreement;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "usage_purposes", columnDefinition = "jsonb", nullable = false)
    private String usagePurposes;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "additional_constraints", columnDefinition = "jsonb", nullable = false)
    private String additionalConstraints;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected PolicyProfileEntity() {}

    PolicyProfileEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ConstraintJson(String leftOperand, String operator, Object rightOperand) {}

    void apply(PolicyProfile profile) {
        PolicyProfileValues v = profile.values();
        code = v.code();
        name = v.name();
        description = v.description();
        accessType = v.accessType();
        membershipRequired = v.membershipRequired();
        frameworkAgreement = v.frameworkAgreement();
        usagePurposes = JSON.writeValueAsString(v.usagePurposes());
        additionalConstraints = JSON.writeValueAsString(v.additionalConstraints().stream()
                .map(c -> new ConstraintJson(c.leftOperand(), c.operator(), c.rightOperand()))
                .toList());
        active = profile.active();
    }

    PolicyProfile toDomain() {
        List<String> purposes = JSON.readValue(usagePurposes, new TypeReference<List<String>>() {});
        List<ConstraintJson> constraints =
                JSON.readValue(additionalConstraints, new TypeReference<List<ConstraintJson>>() {});
        return PolicyProfile.restore(
                getId(),
                new PolicyProfileValues(
                        code,
                        name,
                        description,
                        accessType,
                        membershipRequired,
                        frameworkAgreement,
                        purposes,
                        constraints.stream()
                                .map(c -> new PolicyProfileValues.Constraint(
                                        c.leftOperand(), c.operator(), c.rightOperand()))
                                .toList()),
                active,
                metadata());
    }
}
