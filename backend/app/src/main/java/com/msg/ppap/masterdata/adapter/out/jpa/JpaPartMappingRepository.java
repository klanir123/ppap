package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.platform.domain.Bpns;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaPartMappingRepository implements PartMappingRepository {

    private final PartMappingJpaRepository jpa;
    private final TenantContext tenant;

    JpaPartMappingRepository(PartMappingJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<PartMapping> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(PartMappingEntity::toDomain);
    }

    @Override
    public Optional<PartMapping> findByRelationAndPart(UUID relationId, UUID partId) {
        return jpa.findByTenantIdAndRelationIdAndPartId(tenant.tenantId(), relationId, partId)
                .map(PartMappingEntity::toDomain);
    }

    @Override
    public Optional<PartMapping> findByRelationAndPartnerPartNumber(UUID relationId, PartNumber partnerPartNumber) {
        return jpa.findByTenantIdAndRelationIdAndPartnerPartNumber(
                        tenant.tenantId(), relationId, partnerPartNumber.value())
                .map(PartMappingEntity::toDomain);
    }

    @Override
    public Page<PartMapping> search(UUID relationId, String text, Pageable pageable) {
        return jpa.search(tenant.tenantId(), relationId, Like.contains(text), pageable)
                .map(PartMappingEntity::toDomain);
    }

    @Override
    public Map<UUID, Long> countByRelations(Collection<UUID> relationIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (relationIds.isEmpty()) {
            return counts;
        }
        for (Object[] row : jpa.countByRelations(tenant.tenantId(), relationIds)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    public boolean existsByRelation(UUID relationId) {
        return jpa.existsByTenantIdAndRelationId(tenant.tenantId(), relationId);
    }

    @Override
    public boolean existsByPart(UUID partId) {
        return jpa.existsByTenantIdAndPartId(tenant.tenantId(), partId);
    }

    @Override
    public Page<PartMapping> searchAll(PartnerRole partnerRole, String text, Pageable pageable) {
        return jpa.searchAll(tenant.tenantId(), partnerRole, Like.contains(text), pageable)
                .map(PartMappingEntity::toDomain);
    }

    @Override
    public List<PartMapping> findByPart(UUID partId) {
        return jpa.findByTenantIdAndPartId(tenant.tenantId(), partId).stream()
                .map(PartMappingEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsBySite(Bpns bpns) {
        return jpa.existsBySite(tenant.tenantId(), bpns.value());
    }

    @Override
    public PartMapping save(PartMapping mapping) {
        PartMappingEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), mapping.id())
                .orElseGet(() -> new PartMappingEntity(mapping.id(), tenant.tenantId()));
        entity.apply(mapping);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(PartMapping mapping) {
        jpa.findByTenantIdAndId(tenant.tenantId(), mapping.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
