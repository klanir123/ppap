package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.PartnerValues;
import com.msg.ppap.platform.domain.Bpnl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "md_partner")
class PartnerEntity extends MasterdataEntity {

    @Column(name = "bpnl", nullable = false, length = 16)
    private String bpnl;

    @Column(name = "name", nullable = false)
    private String name;

    /** Sorted, comma-separated role names (see V2__masterdata.sql). */
    @Column(name = "roles", nullable = false, length = 32)
    private String roles;

    @Column(name = "dsp_endpoint", length = 2048)
    private String dspEndpoint;

    @Column(name = "did", length = 2048)
    private String did;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected PartnerEntity() {}

    PartnerEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(Partner partner) {
        PartnerValues v = partner.values();
        bpnl = v.bpnl().value();
        name = v.name();
        roles = v.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        dspEndpoint = v.dspEndpoint() == null ? null : v.dspEndpoint().toString();
        did = v.did();
        contactEmail = v.contactEmail();
        active = partner.active();
    }

    Partner toDomain() {
        return Partner.restore(
                getId(),
                new PartnerValues(
                        new Bpnl(bpnl),
                        name,
                        Arrays.stream(roles.split(","))
                                .filter(role -> !role.isBlank())
                                .map(PartnerRole::valueOf)
                                .collect(Collectors.toSet()),
                        dspEndpoint == null ? null : URI.create(dspEndpoint),
                        did,
                        contactEmail),
                active,
                metadata());
    }
}
