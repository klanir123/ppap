package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.CatalogRepository;
import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaCatalogRepository implements CatalogRepository {

    private static final Sort ORDER = Sort.by("sortOrder", "code");

    private final CatalogElementJpaRepository elements;
    private final SubmissionLevelJpaRepository levels;
    private final SubmissionReasonJpaRepository reasons;
    private final TenantContext tenant;

    JpaCatalogRepository(
            CatalogElementJpaRepository elements,
            SubmissionLevelJpaRepository levels,
            SubmissionReasonJpaRepository reasons,
            TenantContext tenant) {
        this.elements = elements;
        this.levels = levels;
        this.reasons = reasons;
        this.tenant = tenant;
    }

    @Override
    public Optional<CatalogElement> findElementById(UUID id) {
        return elements.findByTenantIdAndId(tenant.tenantId(), id).map(CatalogElementEntity::toDomain);
    }

    @Override
    public Optional<CatalogElement> findElementByCode(CatalogCode code) {
        return elements.findByTenantIdAndCode(tenant.tenantId(), code.value()).map(CatalogElementEntity::toDomain);
    }

    @Override
    public List<CatalogElement> findElements(Boolean active) {
        return elements.list(tenant.tenantId(), active, ORDER).stream()
                .map(CatalogElementEntity::toDomain)
                .toList();
    }

    @Override
    public CatalogElement save(CatalogElement element) {
        CatalogElementEntity entity = elements.findByTenantIdAndId(tenant.tenantId(), element.id())
                .orElseGet(() -> new CatalogElementEntity(element.id(), tenant.tenantId()));
        entity.apply(element);
        return elements.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(CatalogElement element) {
        elements.findByTenantIdAndId(tenant.tenantId(), element.id()).ifPresent(entity -> {
            elements.delete(entity);
            elements.flush();
        });
    }

    @Override
    public Optional<SubmissionLevel> findLevelById(UUID id) {
        return levels.findByTenantIdAndId(tenant.tenantId(), id).map(SubmissionLevelEntity::toDomain);
    }

    @Override
    public Optional<SubmissionLevel> findLevelByCode(CatalogCode code) {
        return levels.findByTenantIdAndCode(tenant.tenantId(), code.value()).map(SubmissionLevelEntity::toDomain);
    }

    @Override
    public List<SubmissionLevel> findLevels(Boolean active) {
        return levels.list(tenant.tenantId(), active, ORDER).stream()
                .map(SubmissionLevelEntity::toDomain)
                .toList();
    }

    @Override
    public SubmissionLevel save(SubmissionLevel level) {
        SubmissionLevelEntity entity = levels.findByTenantIdAndId(tenant.tenantId(), level.id())
                .orElseGet(() -> new SubmissionLevelEntity(level.id(), tenant.tenantId()));
        entity.apply(level);
        return levels.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(SubmissionLevel level) {
        levels.findByTenantIdAndId(tenant.tenantId(), level.id()).ifPresent(entity -> {
            levels.delete(entity);
            levels.flush();
        });
    }

    @Override
    public Optional<SubmissionReason> findReasonById(UUID id) {
        return reasons.findByTenantIdAndId(tenant.tenantId(), id).map(SubmissionReasonEntity::toDomain);
    }

    @Override
    public Optional<SubmissionReason> findReasonByCode(CatalogCode code) {
        return reasons.findByTenantIdAndCode(tenant.tenantId(), code.value()).map(SubmissionReasonEntity::toDomain);
    }

    @Override
    public List<SubmissionReason> findReasons(Boolean active) {
        return reasons.list(tenant.tenantId(), active, ORDER).stream()
                .map(SubmissionReasonEntity::toDomain)
                .toList();
    }

    @Override
    public SubmissionReason save(SubmissionReason reason) {
        SubmissionReasonEntity entity = reasons.findByTenantIdAndId(tenant.tenantId(), reason.id())
                .orElseGet(() -> new SubmissionReasonEntity(reason.id(), tenant.tenantId()));
        entity.apply(reason);
        return reasons.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(SubmissionReason reason) {
        reasons.findByTenantIdAndId(tenant.tenantId(), reason.id()).ifPresent(entity -> {
            reasons.delete(entity);
            reasons.flush();
        });
    }
}
