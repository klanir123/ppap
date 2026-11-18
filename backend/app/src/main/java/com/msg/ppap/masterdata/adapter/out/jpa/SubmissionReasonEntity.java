package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import com.msg.ppap.masterdata.domain.SubmissionReasonValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "md_submission_reason")
class SubmissionReasonEntity extends MasterdataEntity {

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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected SubmissionReasonEntity() {}

    SubmissionReasonEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(SubmissionReason reason) {
        SubmissionReasonValues v = reason.values();
        code = v.code().value();
        nameDe = v.nameDe();
        nameEn = v.nameEn();
        descriptionDe = v.descriptionDe();
        descriptionEn = v.descriptionEn();
        sortOrder = v.sortOrder();
        active = reason.active();
    }

    SubmissionReason toDomain() {
        return SubmissionReason.restore(
                getId(),
                new SubmissionReasonValues(
                        new CatalogCode(code), nameDe, nameEn, descriptionDe, descriptionEn, sortOrder),
                active,
                metadata());
    }
}
