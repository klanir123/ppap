package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.LevelScheme;
import com.msg.ppap.masterdata.domain.Requirement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import com.msg.ppap.masterdata.domain.SubmissionLevelValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "md_submission_level")
class SubmissionLevelEntity extends MasterdataEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name_de", nullable = false)
    private String nameDe;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_de", length = 4000)
    private String descriptionDe;

    @Column(name = "description_en", length = 4000)
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 16)
    private LevelScheme scheme;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Element code → requirement (see V2__masterdata.sql for why this is JSONB and not a join table). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements", nullable = false)
    private Map<String, String> requirements;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected SubmissionLevelEntity() {}

    SubmissionLevelEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(SubmissionLevel level) {
        SubmissionLevelValues v = level.values();
        code = v.code().value();
        nameDe = v.nameDe();
        nameEn = v.nameEn();
        descriptionDe = v.descriptionDe();
        descriptionEn = v.descriptionEn();
        scheme = v.scheme();
        sortOrder = v.sortOrder();
        var matrix = new TreeMap<String, String>();
        v.requirements().forEach((elementCode, requirement) -> matrix.put(elementCode.value(), requirement.name()));
        requirements = matrix;
        active = level.active();
    }

    SubmissionLevel toDomain() {
        var matrix = new HashMap<CatalogCode, Requirement>();
        if (requirements != null) {
            requirements.forEach((elementCode, requirement) ->
                    matrix.put(new CatalogCode(elementCode), Requirement.valueOf(requirement)));
        }
        return SubmissionLevel.restore(
                getId(),
                new SubmissionLevelValues(
                        new CatalogCode(code), nameDe, nameEn, descriptionDe, descriptionEn, scheme, sortOrder, matrix),
                active,
                metadata());
    }
}
