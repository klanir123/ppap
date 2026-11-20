package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Ids;
import java.util.UUID;

/**
 * Policy profile of customer-supplier relations (UC-10, E5-04): which access and usage policy the own connector
 * offers to a partner. Data, not code; the profile {@value #DEFAULT_CODE} is used for the shared assets and for
 * relations without an explicit profile.
 */
public final class PolicyProfile extends MasterdataAggregate<PolicyProfileValues> {

    /** Code of the default profile (created by the database migration, cannot be deleted or deactivated). */
    public static final String DEFAULT_CODE = RelationValues.DEFAULT_POLICY_PROFILE;

    private PolicyProfile(UUID id, PolicyProfileValues values, boolean active, Metadata metadata) {
        super(id, values, active, metadata);
    }

    public static PolicyProfile create(PolicyProfileValues values, boolean active) {
        return new PolicyProfile(Ids.newId(), values, active, null);
    }

    public static PolicyProfile restore(UUID id, PolicyProfileValues values, boolean active, Metadata metadata) {
        return new PolicyProfile(id, values, active, metadata);
    }

    /** Whether this is the default profile. */
    public boolean isDefault() {
        return DEFAULT_CODE.equals(values().code());
    }

    @Override
    public MasterdataType type() {
        return MasterdataType.POLICY_PROFILE;
    }

    @Override
    public String businessKey() {
        return values().code();
    }
}
