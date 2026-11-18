package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.Site;
import com.msg.ppap.masterdata.domain.SiteValues;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Bpns;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "md_site")
class SiteEntity extends MasterdataEntity {

    @Column(name = "bpns", nullable = false, length = 16)
    private String bpns;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_bpnl", nullable = false, length = 16)
    private String ownerBpnl;

    @Column(name = "street")
    private String street;

    @Column(name = "postal_code", length = 64)
    private String postalCode;

    @Column(name = "city")
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected SiteEntity() {}

    SiteEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(Site site) {
        SiteValues v = site.values();
        bpns = v.bpns().value();
        name = v.name();
        ownerBpnl = v.ownerBpnl().value();
        street = v.street();
        postalCode = v.postalCode();
        city = v.city();
        country = v.country();
        active = site.active();
    }

    Site toDomain() {
        return Site.restore(
                getId(),
                new SiteValues(new Bpns(bpns), name, new Bpnl(ownerBpnl), street, postalCode, city, country),
                active,
                metadata());
    }
}
