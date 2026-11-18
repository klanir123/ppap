package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.PartMappingValues;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.platform.domain.Bpns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "md_part_mapping")
class PartMappingEntity extends MasterdataEntity {

    @Column(name = "relation_id", nullable = false)
    private UUID relationId;

    @Column(name = "part_id", nullable = false)
    private UUID partId;

    @Column(name = "partner_part_number", nullable = false, length = 64)
    private String partnerPartNumber;

    @Column(name = "own_site_bpns", length = 16)
    private String ownSiteBpns;

    @Column(name = "partner_site_bpns", length = 16)
    private String partnerSiteBpns;

    protected PartMappingEntity() {}

    PartMappingEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(PartMapping mapping) {
        PartMappingValues v = mapping.values();
        relationId = v.relationId();
        partId = v.partId();
        partnerPartNumber = v.partnerPartNumber().value();
        ownSiteBpns = v.ownSiteBpns() == null ? null : v.ownSiteBpns().value();
        partnerSiteBpns =
                v.partnerSiteBpns() == null ? null : v.partnerSiteBpns().value();
    }

    PartMapping toDomain() {
        return PartMapping.restore(
                getId(),
                new PartMappingValues(
                        relationId,
                        partId,
                        new PartNumber(partnerPartNumber),
                        ownSiteBpns == null ? null : new Bpns(ownSiteBpns),
                        partnerSiteBpns == null ? null : new Bpns(partnerSiteBpns)),
                metadata());
    }
}
