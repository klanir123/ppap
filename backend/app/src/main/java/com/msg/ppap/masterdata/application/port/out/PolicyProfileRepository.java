package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.PolicyProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of policy profiles; scoped to the current tenant. */
public interface PolicyProfileRepository {

    Optional<PolicyProfile> findById(UUID id);

    Optional<PolicyProfile> findByCode(String code);

    /** All profiles, sorted by code; {@code active} filters if not {@code null}. */
    List<PolicyProfile> findAll(Boolean active);

    PolicyProfile save(PolicyProfile profile);

    void delete(PolicyProfile profile);
}
