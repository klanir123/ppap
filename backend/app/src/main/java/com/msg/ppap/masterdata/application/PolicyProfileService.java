package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.PolicyProfileRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.PolicyProfile;
import com.msg.ppap.masterdata.domain.PolicyProfileValues;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ReferencedException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for policy profiles (UC-10, docs/api/cascade-transparency-api.md §3). Every change publishes a {@code
 * MasterdataChanged} event (object type {@code POLICY_PROFILE}) that triggers the EDC reconciliation.
 */
@Service
@Transactional(noRollbackFor = PpapException.class)
public class PolicyProfileService extends MasterdataService<PolicyProfile, PolicyProfileValues> {

    /** The default profile cannot be deleted, deactivated or renamed. */
    public static final String DEFAULT_PROFILE_PROTECTED = "DEFAULT_PROFILE_PROTECTED";

    private final PolicyProfileRepository profiles;
    private final RelationRepository relations;

    public PolicyProfileService(
            PolicyProfileRepository profiles, RelationRepository relations, MasterdataEvents events) {
        super(events);
        this.profiles = profiles;
        this.relations = relations;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PolicyProfile get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public List<PolicyProfile> list(Boolean active) {
        return profiles.findAll(active);
    }

    public PolicyProfile create(PolicyProfileCommand command) {
        return doCreate(command.toValues(), command.active());
    }

    public PolicyProfile update(UUID id, PolicyProfileCommand command, Long expectedVersion) {
        PolicyProfile existing = get(id);
        if (existing.isDefault() && Boolean.FALSE.equals(command.active())) {
            throw protectedDefault("deactivated");
        }
        return doUpdate(id, command.toValues(), command.active(), expectedVersion);
    }

    @Override
    public boolean delete(UUID id, Long expectedVersion) {
        if (get(id).isDefault()) {
            throw protectedDefault("deleted");
        }
        return super.delete(id, expectedVersion);
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.POLICY_PROFILE;
    }

    @Override
    protected String keyField() {
        return "code";
    }

    @Override
    protected Object keyOf(PolicyProfileValues values) {
        return values.code();
    }

    @Override
    protected Optional<PolicyProfile> findById(UUID id) {
        return profiles.findById(id);
    }

    @Override
    protected Optional<PolicyProfile> findByKey(PolicyProfileValues values) {
        return profiles.findByCode(values.code());
    }

    @Override
    protected PolicyProfile newAggregate(PolicyProfileValues values, boolean active) {
        return PolicyProfile.create(values, active);
    }

    @Override
    protected PolicyProfile persist(PolicyProfile aggregate) {
        return profiles.save(aggregate);
    }

    @Override
    protected void remove(PolicyProfile aggregate) {
        profiles.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(PolicyProfile aggregate) {
        return relations.existsByPolicyProfile(aggregate.values().code());
    }

    @Override
    protected void checkKeyChange(PolicyProfile existing, PolicyProfileValues newValues) {
        if (existing.isDefault()) {
            throw protectedDefault("renamed");
        }
        if (isReferenced(existing)) {
            throw new ReferencedException(
                    "Code of policy profile '" + existing.businessKey() + "' cannot change: used by relations");
        }
    }

    private static PpapException protectedDefault(String what) {
        return new PpapException(
                PpapException.Kind.CONFLICT,
                DEFAULT_PROFILE_PROTECTED,
                "The policy profile '" + PolicyProfile.DEFAULT_CODE + "' cannot be " + what);
    }
}
