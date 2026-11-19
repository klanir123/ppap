package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.platform.domain.Bpns;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of part mappings; all operations are scoped to the current tenant. */
public interface PartMappingRepository {

    Optional<PartMapping> findById(UUID id);

    Optional<PartMapping> findByRelationAndPart(UUID relationId, UUID partId);

    Optional<PartMapping> findByRelationAndPartnerPartNumber(UUID relationId, PartNumber partnerPartNumber);

    /**
     * Mappings of a relation.
     *
     * @param text filter on partner part number and own part number/name, or {@code null}
     */
    Page<PartMapping> search(UUID relationId, String text, Pageable pageable);

    /** Number of mappings per relation. */
    Map<UUID, Long> countByRelations(Collection<UUID> relationIds);

    boolean existsByRelation(UUID relationId);

    boolean existsByPart(UUID partId);

    /**
     * Mappings over all active relations.
     *
     * @param partnerRole role of the partner, or {@code null}
     * @param text filter on partner part number, own part number/name and partner name, or {@code null}
     */
    Page<PartMapping> searchAll(PartnerRole partnerRole, String text, Pageable pageable);

    /** All mappings of an own part. */
    List<PartMapping> findByPart(UUID partId);

    boolean existsBySite(Bpns bpns);

    PartMapping save(PartMapping mapping);

    void delete(PartMapping mapping);
}
