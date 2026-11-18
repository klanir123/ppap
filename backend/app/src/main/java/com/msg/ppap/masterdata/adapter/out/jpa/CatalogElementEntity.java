package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.CatalogElementValues;
import com.msg.ppap.masterdata.domain.EvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "md_catalog_element")
class CatalogElementEntity extends MasterdataEntity {

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

    @Column(name = "reference_vda")
    private String referenceVda;

    @Column(name = "reference_aiag")
    private String referenceAiag;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 32)
    private EvidenceType evidenceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_schema")
    private Map<String, Object> valueSchema;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected CatalogElementEntity() {}

    CatalogElementEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(CatalogElement element) {
        CatalogElementValues v = element.values();
        code = v.code().value();
        nameDe = v.nameDe();
        nameEn = v.nameEn();
        descriptionDe = v.descriptionDe();
        descriptionEn = v.descriptionEn();
        referenceVda = v.referenceVda();
        referenceAiag = v.referenceAiag();
        evidenceType = v.evidenceType();
        valueSchema = v.valueSchema();
        sortOrder = v.sortOrder();
        active = element.active();
    }

    CatalogElement toDomain() {
        return CatalogElement.restore(
                getId(),
                new CatalogElementValues(
                        new CatalogCode(code),
                        nameDe,
                        nameEn,
                        descriptionDe,
                        descriptionEn,
                        referenceVda,
                        referenceAiag,
                        evidenceType,
                        valueSchema,
                        sortOrder),
                active,
                metadata());
    }
}
