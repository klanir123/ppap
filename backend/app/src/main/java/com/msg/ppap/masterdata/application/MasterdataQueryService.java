package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.application.port.out.CatalogRepository;
import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartRepository;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the public {@link MasterdataQueries} API. */
@Service
@Transactional(readOnly = true)
public class MasterdataQueryService implements MasterdataQueries {

    private final PartRepository parts;
    private final PartnerRepository partners;
    private final RelationRepository relations;
    private final PartMappingRepository mappings;
    private final CatalogRepository catalog;

    public MasterdataQueryService(
            PartRepository parts,
            PartnerRepository partners,
            RelationRepository relations,
            PartMappingRepository mappings,
            CatalogRepository catalog) {
        this.parts = parts;
        this.partners = partners;
        this.relations = relations;
        this.mappings = mappings;
        this.catalog = catalog;
    }

    @Override
    public Optional<PartInfo> findPart(UUID id) {
        return id == null ? Optional.empty() : parts.findById(id).map(MasterdataQueryService::part);
    }

    @Override
    public Optional<PartInfo> findPartByNumber(String partNumber) {
        return parse(partNumber, PartNumber::new)
                .flatMap(parts::findByPartNumber)
                .map(MasterdataQueryService::part);
    }

    @Override
    public Optional<PartnerInfo> findPartner(String bpnl) {
        return parse(bpnl, Bpnl::new).flatMap(partners::findByBpnl).map(MasterdataQueryService::partner);
    }

    @Override
    public Optional<RelationInfo> findRelation(UUID id) {
        return id == null ? Optional.empty() : relations.findById(id).map(this::relation);
    }

    @Override
    public Optional<RelationInfo> findRelation(String partnerBpnl, String partnerRole) {
        Optional<PartnerRole> role = parse(partnerRole, r -> PartnerRole.valueOf(r.toUpperCase(Locale.ROOT)));
        return parse(partnerBpnl, Bpnl::new)
                .flatMap(bpnl -> role.flatMap(r -> relations.findByPartner(bpnl, r)))
                .map(this::relation);
    }

    @Override
    public Optional<PartMappingInfo> findMapping(UUID relationId, UUID partId) {
        return mappings.findByRelationAndPart(relationId, partId).map(this::mapping);
    }

    @Override
    public Optional<PartMappingInfo> findMappingByPartnerPartNumber(UUID relationId, String partnerPartNumber) {
        return parse(partnerPartNumber, PartNumber::new)
                .flatMap(number -> mappings.findByRelationAndPartnerPartNumber(relationId, number))
                .map(this::mapping);
    }

    @Override
    public List<PartMappingInfo> findMappingsOfPart(UUID partId) {
        return mappings.findByPart(partId).stream().map(this::mapping).toList();
    }

    @Override
    public Optional<CatalogElementInfo> findElement(String code) {
        return parse(code, CatalogCode::new).flatMap(catalog::findElementByCode).map(MasterdataQueryService::element);
    }

    @Override
    public List<CatalogElementInfo> elements() {
        return catalog.findElements(null).stream()
                .map(MasterdataQueryService::element)
                .toList();
    }

    @Override
    public Optional<LevelInfo> findLevel(String code) {
        return parse(code, CatalogCode::new).flatMap(catalog::findLevelByCode).map(level -> {
            var v = level.values();
            var matrix = new TreeMap<String, String>();
            v.requirements().forEach((element, requirement) -> matrix.put(element.value(), requirement.name()));
            return new LevelInfo(v.code().value(), v.nameDe(), v.nameEn(), level.active(), matrix);
        });
    }

    @Override
    public Optional<ReasonInfo> findReason(String code) {
        return parse(code, CatalogCode::new)
                .flatMap(catalog::findReasonByCode)
                .map(reason -> new ReasonInfo(
                        reason.values().code().value(),
                        reason.values().nameDe(),
                        reason.values().nameEn(),
                        reason.active()));
    }

    @Override
    public List<PartnerInfo> exchangePartners() {
        var related = relations
                .search(null, true, null, null, org.springframework.data.domain.Pageable.unpaged())
                .getContent()
                .stream()
                .map(r -> r.values().partnerBpnl())
                .distinct()
                .toList();
        if (related.isEmpty()) {
            return List.of();
        }
        return partners.findAllByBpnl(related).stream()
                .filter(Partner::exchangeReady)
                .map(MasterdataQueryService::partner)
                .sorted(java.util.Comparator.comparing(PartnerInfo::bpnl))
                .toList();
    }

    private static PartInfo part(Part part) {
        var v = part.values();
        return new PartInfo(part.id(), v.partNumber().value(), v.nameDe(), v.nameEn(), part.active());
    }

    private static PartnerInfo partner(Partner partner) {
        var v = partner.values();
        return new PartnerInfo(
                v.bpnl().value(),
                v.name(),
                v.roles().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()),
                partner.active(),
                partner.exchangeReady(),
                v.dspEndpoint() == null ? null : v.dspEndpoint().toString(),
                v.did());
    }

    private RelationInfo relation(Relation relation) {
        var v = relation.values();
        String name = partners.findByBpnl(v.partnerBpnl())
                .map(p -> p.values().name())
                .orElse(v.partnerBpnl().value());
        return new RelationInfo(
                relation.id(),
                v.partnerBpnl().value(),
                name,
                v.partnerRole().name(),
                relation.active(),
                v.exchangeMode().name());
    }

    private PartMappingInfo mapping(PartMapping mapping) {
        var v = mapping.values();
        String partNumber = parts.findById(v.partId())
                .map(p -> p.values().partNumber().value())
                .orElse(null);
        return new PartMappingInfo(
                mapping.id(),
                v.relationId(),
                v.partId(),
                partNumber,
                v.partnerPartNumber().value());
    }

    private static CatalogElementInfo element(CatalogElement element) {
        var v = element.values();
        return new CatalogElementInfo(
                v.code().value(),
                v.nameDe(),
                v.nameEn(),
                v.evidenceType().name(),
                v.valueSchema(),
                v.sortOrder(),
                element.active());
    }

    private static <T> Optional<T> parse(String raw, Function<String, T> parser) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(raw.strip()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
