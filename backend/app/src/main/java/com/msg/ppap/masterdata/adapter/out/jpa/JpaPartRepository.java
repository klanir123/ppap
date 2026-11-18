package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PartRepository;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class JpaPartRepository implements PartRepository {

    private final PartJpaRepository jpa;
    private final TenantContext tenant;

    JpaPartRepository(PartJpaRepository jpa, TenantContext tenant) {
        this.jpa = jpa;
        this.tenant = tenant;
    }

    @Override
    public Optional<Part> findById(UUID id) {
        return jpa.findByTenantIdAndId(tenant.tenantId(), id).map(PartEntity::toDomain);
    }

    @Override
    public Optional<Part> findByPartNumber(PartNumber partNumber) {
        return jpa.findByTenantIdAndPartNumber(tenant.tenantId(), partNumber.value())
                .map(PartEntity::toDomain);
    }

    @Override
    public List<Part> findAllById(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByTenantIdAndIdIn(tenant.tenantId(), ids).stream()
                .map(PartEntity::toDomain)
                .toList();
    }

    @Override
    public Page<Part> search(String text, Boolean active, Bpnl partnerBpnl, Pageable pageable) {
        return jpa.search(
                        tenant.tenantId(),
                        Like.contains(text),
                        active,
                        partnerBpnl == null ? null : partnerBpnl.value(),
                        pageable)
                .map(PartEntity::toDomain);
    }

    @Override
    public Part save(Part part) {
        PartEntity entity = jpa.findByTenantIdAndId(tenant.tenantId(), part.id())
                .orElseGet(() -> new PartEntity(part.id(), tenant.tenantId()));
        entity.apply(part);
        return jpa.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(Part part) {
        jpa.findByTenantIdAndId(tenant.tenantId(), part.id()).ifPresent(entity -> {
            jpa.delete(entity);
            jpa.flush();
        });
    }
}
