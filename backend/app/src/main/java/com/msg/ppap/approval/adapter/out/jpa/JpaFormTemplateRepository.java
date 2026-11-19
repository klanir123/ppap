package com.msg.ppap.approval.adapter.out.jpa;

import com.msg.ppap.approval.application.port.out.FormTemplateRepository;
import com.msg.ppap.approval.domain.FormTemplate;
import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.Ids;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JpaFormTemplateRepository implements FormTemplateRepository {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ApFormTemplateJpaRepository jpa;
    private final TenantContext tenant;
    private final ActorProvider actors;
    private final Clock clock;

    JpaFormTemplateRepository(
            ApFormTemplateJpaRepository jpa, TenantContext tenant, ActorProvider actors, Clock clock) {
        this.jpa = jpa;
        this.tenant = tenant;
        this.actors = actors;
        this.clock = clock;
    }

    @Override
    public List<FormTemplate> findAll() {
        return jpa.findByTenantIdOrderByCode(tenant.tenantId()).stream()
                .map(JpaFormTemplateRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<FormTemplate> findByCode(String code) {
        return jpa.findByTenantIdAndCode(tenant.tenantId(), code).map(JpaFormTemplateRepository::toDomain);
    }

    @Override
    public UpsertOutcome upsert(FormTemplate template) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String actor = actors.currentActor();
        Optional<ApFormTemplateEntity> existing = jpa.findByTenantIdAndCode(tenant.tenantId(), template.code());
        if (existing.isPresent() && Objects.equals(toDomain(existing.get()), template)) {
            return UpsertOutcome.UNCHANGED;
        }
        ApFormTemplateEntity e = existing.orElseGet(() -> {
            var created = new ApFormTemplateEntity();
            created.id = Ids.newId();
            created.tenantId = tenant.tenantId();
            created.createdAt = now;
            created.createdBy = actor;
            created.code = template.code();
            return created;
        });
        e.nameDe = template.nameDe();
        e.nameEn = template.nameEn();
        e.active = template.active();
        e.definition = MAPPER.writeValueAsString(Map.of(
                "marker", template.marker(),
                "header", template.header(),
                "elementTable", template.elementTable(),
                "selfAssessmentValues", template.selfAssessmentValues()));
        e.updatedAt = now;
        e.updatedBy = actor;
        jpa.saveAndFlush(e);
        return existing.isPresent() ? UpsertOutcome.UPDATED : UpsertOutcome.CREATED;
    }

    record Definition(
            FormTemplate.Marker marker,
            List<FormTemplate.HeaderField> header,
            FormTemplate.ElementTable elementTable,
            java.util.LinkedHashMap<String, String> selfAssessmentValues) {}

    private static FormTemplate toDomain(ApFormTemplateEntity e) {
        Definition d = MAPPER.readValue(e.definition, Definition.class);
        return new FormTemplate(
                e.code,
                e.nameDe,
                e.nameEn,
                d.marker(),
                d.header(),
                d.elementTable(),
                d.selfAssessmentValues(),
                e.active);
    }
}
