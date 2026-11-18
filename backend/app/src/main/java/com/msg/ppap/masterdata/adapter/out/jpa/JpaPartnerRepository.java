package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaPartnerRepository implements PartnerRepository {

    private final PartnerJpaRepository jpa;
    private final TenantContext tenant;

    JpaPartnerRepository(PartnerJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<Partner> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(PartnerEntity::toDomain);
    }

    @Override
    public Optional<Partner> findByBpnl(Bpnl bpnl) {
        return jpa.findByTenantIdAndBpnl(tenant.tenantId(), bpnl.value()).map(PartnerEntity::toDomain);
    }

    @Override
    public List<Partner> findAllByBpnl(Collection<Bpnl> bpnls) {
        if (bpnls.isEmpty()) {
            return List.of();
        }
        return jpa
                .findByTenantIdAndBpnlIn(
                        tenant.tenantId(), bpnls.stream().map(Bpnl::value).toList())
                .stream()
                .map(PartnerEntity::toDomain)
                .toList();
    }

    @Override
    public Page<Partner> search(String text, Boolean active, Pageable pageable) {
        return jpa.search(tenant.tenantId(), Like.contains(text), active, pageable)
                .map(PartnerEntity::toDomain);
    }

    @Override
    public Partner save(Partner partner) {
        PartnerEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), partner.id())
                .orElseGet(() -> new PartnerEntity(partner.id(), tenant.tenantId()));
        entity.apply(partner);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(Partner partner) {
        jpa.findByTenantIdAndId(tenant.tenantId(), partner.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
