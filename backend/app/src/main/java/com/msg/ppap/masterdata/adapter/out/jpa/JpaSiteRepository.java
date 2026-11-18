package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.SiteRepository;
import com.msg.ppap.masterdata.domain.Site;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Bpns;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaSiteRepository implements SiteRepository {

    private final SiteJpaRepository jpa;
    private final TenantContext tenant;

    JpaSiteRepository(SiteJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<Site> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(SiteEntity::toDomain);
    }

    @Override
    public Optional<Site> findByBpns(Bpns bpns) {
        return jpa.findByTenantIdAndBpns(tenant.tenantId(), bpns.value()).map(SiteEntity::toDomain);
    }

    @Override
    public Page<Site> search(String text, Boolean active, Bpnl ownerBpnl, Pageable pageable) {
        return jpa.search(
                        tenant.tenantId(),
                        Like.contains(text),
                        active,
                        ownerBpnl == null ? null : ownerBpnl.value(),
                        pageable)
                .map(SiteEntity::toDomain);
    }

    @Override
    public boolean existsByOwner(Bpnl ownerBpnl) {
        return jpa.existsByTenantIdAndOwnerBpnl(tenant.tenantId(), ownerBpnl.value());
    }

    @Override
    public Site save(Site site) {
        SiteEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), site.id())
                .orElseGet(() -> new SiteEntity(site.id(), tenant.tenantId()));
        entity.apply(site);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(Site site) {
        jpa.findByTenantIdAndId(tenant.tenantId(), site.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
