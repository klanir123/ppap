package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of customer-supplier relations; all operations are scoped to the current tenant. */
public interface RelationRepository {

    Optional<Relation> findById(UUID id);

    Optional<Relation> findByPartner(Bpnl partnerBpnl, PartnerRole partnerRole);

    Page<Relation> search(String text, Boolean active, Bpnl partnerBpnl, PartnerRole partnerRole, Pageable pageable);

    boolean existsByPartner(Bpnl partnerBpnl);

    /** Whether a relation references the policy profile. */
    boolean existsByPolicyProfile(String code);

    /** All relations of the tenant (topology, provisioning). */
    java.util.List<Relation> findAll();

    Relation save(Relation relation);

    void delete(Relation relation);
}
