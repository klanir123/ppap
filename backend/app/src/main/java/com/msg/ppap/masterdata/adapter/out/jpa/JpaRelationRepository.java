package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaRelationRepository implements RelationRepository {

    private final RelationJpaRepository jpa;
    private final TenantContext tenant;

    JpaRelationRepository(RelationJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<Relation> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(RelationEntity::toDomain);
    }

    @Override
    public Optional<Relation> findByPartner(Bpnl partnerBpnl, PartnerRole partnerRole) {
        return jpa.findByTenantIdAndPartnerBpnlAndPartnerRole(tenant.tenantId(), partnerBpnl.value(), partnerRole)
                .map(RelationEntity::toDomain);
    }

    @Override
    public Page<Relation> search(
            String text, Boolean active, Bpnl partnerBpnl, PartnerRole partnerRole, Pageable pageable) {
        return jpa.search(
                        tenant.tenantId(),
                        Like.contains(text),
                        active,
                        partnerBpnl == null ? null : partnerBpnl.value(),
                        partnerRole,
                        pageable)
                .map(RelationEntity::toDomain);
    }

    @Override
    public boolean existsByPartner(Bpnl partnerBpnl) {
        return jpa.existsByTenantIdAndPartnerBpnl(tenant.tenantId(), partnerBpnl.value());
    }

    @Override
    public boolean existsByPolicyProfile(String code) {
        return jpa.existsByTenantIdAndPolicyProfile(tenant.tenantId(), code);
    }

    @Override
    public java.util.List<Relation> findAll() {
        return jpa.findByTenantIdOrderByPartnerBpnlAscPartnerRoleAsc(tenant.tenantId()).stream()
                .map(RelationEntity::toDomain)
                .toList();
    }

    @Override
    public Relation save(Relation relation) {
        RelationEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), relation.id())
                .orElseGet(() -> new RelationEntity(relation.id(), tenant.tenantId()));
        entity.apply(relation);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(Relation relation) {
        jpa.findByTenantIdAndId(tenant.tenantId(), relation.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
