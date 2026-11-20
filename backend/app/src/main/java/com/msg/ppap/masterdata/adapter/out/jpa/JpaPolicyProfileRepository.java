package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PolicyProfileRepository;
import com.msg.ppap.masterdata.domain.PolicyProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaPolicyProfileRepository implements PolicyProfileRepository {

    private final PolicyProfileJpaRepository jpa;
    private final TenantContext tenant;

    JpaPolicyProfileRepository(PolicyProfileJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<PolicyProfile> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(PolicyProfileEntity::toDomain);
    }

    @Override
    public Optional<PolicyProfile> findByCode(String code) {
        return code == null
                ? Optional.empty()
                : jpa.findByTenantIdAndCode(tenant.tenantId(), code).map(PolicyProfileEntity::toDomain);
    }

    @Override
    public List<PolicyProfile> findAll(Boolean active) {
        return jpa.findByTenantIdOrderByCode(tenant.tenantId()).stream()
                .map(PolicyProfileEntity::toDomain)
                .filter(p -> active == null || p.active() == active)
                .toList();
    }

    @Override
    public PolicyProfile save(PolicyProfile profile) {
        PolicyProfileEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), profile.id())
                .orElseGet(() -> new PolicyProfileEntity(profile.id(), tenant.tenantId()));
        entity.apply(profile);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(PolicyProfile profile) {
        jpa.findByTenantIdAndId(tenant.tenantId(), profile.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
