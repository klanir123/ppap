package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartRepository;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Validation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search of part mappings across all relations (request wizard: part → supplier). */
@Service
@Transactional(readOnly = true)
public class PartMappingSearchService {

    private final PartMappingRepository mappings;
    private final RelationRepository relations;
    private final PartRepository parts;
    private final PartnerRepository partners;

    public PartMappingSearchService(
            PartMappingRepository mappings,
            RelationRepository relations,
            PartRepository parts,
            PartnerRepository partners) {
        this.mappings = mappings;
        this.relations = relations;
        this.parts = parts;
        this.partners = partners;
    }

    /**
     * Searches mappings of active relations.
     *
     * @param partnerRole {@code CUSTOMER}, {@code SUPPLIER} or {@code null}
     */
    public Page<PartMappingSearchView> search(String partnerRole, String text, Pageable pageable) {
        var v = new Validation();
        PartnerRole role = v.optionalEnum("partnerRole", partnerRole, PartnerRole.class, null);
        v.throwIfInvalid();
        Page<PartMapping> page = mappings.searchAll(role, Validation.trimToNull(text), pageable);
        Map<UUID, Relation> relationById = new HashMap<>();
        for (PartMapping m : page.getContent()) {
            relationById.computeIfAbsent(
                    m.values().relationId(), id -> relations.findById(id).orElse(null));
        }
        Set<Bpnl> bpnls = relationById.values().stream()
                .filter(r -> r != null)
                .map(r -> r.values().partnerBpnl())
                .collect(Collectors.toSet());
        Map<Bpnl, Partner> partnerByBpnl = partners.findAllByBpnl(bpnls).stream()
                .collect(Collectors.toMap(p -> p.values().bpnl(), Function.identity()));
        Map<UUID, Part> partById = parts
                .findAllById(
                        page.getContent().stream().map(m -> m.values().partId()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Part::id, Function.identity()));
        var content = page.getContent().stream()
                .map(m -> {
                    Relation relation = relationById.get(m.values().relationId());
                    Bpnl bpnl = relation == null ? null : relation.values().partnerBpnl();
                    return new PartMappingSearchView(
                            m,
                            bpnl == null ? null : bpnl.value(),
                            bpnl == null ? null : partnerByBpnl.get(bpnl),
                            partById.get(m.values().partId()));
                })
                .toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }
}
