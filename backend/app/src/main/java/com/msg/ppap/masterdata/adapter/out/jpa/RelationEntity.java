package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.ExchangeMode;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.masterdata.domain.RelationValues;
import com.msg.ppap.platform.domain.Bpnl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "md_relation")
class RelationEntity extends MasterdataEntity {

    @Column(name = "partner_bpnl", nullable = false, length = 16)
    private String partnerBpnl;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_role", nullable = false, length = 16)
    private PartnerRole partnerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "exchange_mode", nullable = false, length = 32)
    private ExchangeMode exchangeMode;

    @Column(name = "policy_profile", nullable = false, length = 64)
    private String policyProfile;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected RelationEntity() {}

    RelationEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(Relation relation) {
        RelationValues v = relation.values();
        partnerBpnl = v.partnerBpnl().value();
        partnerRole = v.partnerRole();
        exchangeMode = v.exchangeMode();
        policyProfile = v.policyProfile();
        active = relation.active();
    }

    Relation toDomain() {
        return Relation.restore(
                getId(),
                new RelationValues(new Bpnl(partnerBpnl), partnerRole, exchangeMode, policyProfile),
                active,
                metadata());
    }
}
