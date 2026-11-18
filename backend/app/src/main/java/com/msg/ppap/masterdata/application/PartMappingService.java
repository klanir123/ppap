package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.application.port.out.SiteRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.PartMappingValues;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.Bpns;
import com.msg.ppap.platform.domain.DuplicateKeyException;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for part mappings within a relation (UC-01) and the lookup used when receiving requests (UC-04). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class PartMappingService extends MasterdataService<PartMapping, PartMappingValues> {

    private final PartMappingRepository mappings;
    private final RelationRepository relations;
    private final PartRepository parts;
    private final SiteRepository sites;
    private final RelationService relationService;

    public PartMappingService(
            PartMappingRepository mappings,
            RelationRepository relations,
            PartRepository parts,
            SiteRepository sites,
            RelationService relationService,
            MasterdataEvents events) {
        super(events);
        this.mappings = mappings;
        this.relations = relations;
        this.parts = parts;
        this.sites = sites;
        this.relationService = relationService;
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Page<PartMappingView> search(UUID relationId, String text, Pageable pageable) {
        requireRelation(relationId);
        Page<PartMapping> page = mappings.search(relationId, Keys.text(text), pageable);
        return new PageImpl<>(views(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PartMappingView view(UUID relationId, UUID id) {
        return view(getInRelation(relationId, id));
    }

    public PartMappingView create(UUID relationId, PartMappingCommand command) {
        requireRelation(relationId);
        return view(doCreate(toValues(relationId, command), true));
    }

    public PartMappingView update(UUID relationId, UUID id, PartMappingCommand command, Long expectedVersion) {
        getInRelation(relationId, id);
        return view(doUpdate(id, toValues(relationId, command), true, expectedVersion));
    }

    /** Deletes the mapping physically. */
    public void delete(UUID relationId, UUID id, Long expectedVersion) {
        getInRelation(relationId, id);
        delete(id, expectedVersion);
    }

    /** Upsert by (relation, part) used by the import. */
    public UpsertResult<PartMapping> upsert(UUID relationId, PartMappingCommand command) {
        return doUpsert(toValues(relationId, command), true, null);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PlanResult plan(UUID relationId, PartMappingCommand command) {
        return doPlan(toValues(relationId, command), true);
    }

    /**
     * Resolves the mapping for a partner part number (UC-04).
     *
     * @throws NotFoundException if relation or mapping do not exist
     */
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PartMappingView resolve(String partnerBpnl, String partnerRole, String partnerPartNumber) {
        Relation relation = relationService.findByPartner(partnerBpnl, partnerRole);
        String number = Validation.trimToNull(partnerPartNumber);
        if (number == null) {
            throw ValidationException.of("partnerPartNumber", ViolationCodes.REQUIRED, "must not be blank");
        }
        PartNumber parsed;
        try {
            parsed = new PartNumber(number);
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of("partnerPartNumber", ViolationCodes.INVALID_FORMAT, "invalid part number");
        }
        return mappings.findByRelationAndPartnerPartNumber(relation.id(), parsed)
                .map(this::view)
                .orElseThrow(() -> new NotFoundException(type().label(), partnerPartNumber));
    }

    private PartMappingValues toValues(UUID relationId, PartMappingCommand command) {
        UUID partId = resolvePart(command);
        return PartMappingValues.parse(
                relationId, partId, command.partnerPartNumber(), command.ownSiteBpns(), command.partnerSiteBpns());
    }

    private UUID resolvePart(PartMappingCommand command) {
        String rawId = Validation.trimToNull(command.partId());
        if (rawId != null) {
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                throw ValidationException.of("partId", ViolationCodes.INVALID_FORMAT, "must be a UUID");
            }
            return parts.findById(id)
                    .map(Part::id)
                    .orElseThrow(
                            () -> ValidationException.of("partId", ViolationCodes.UNKNOWN_REFERENCE, "unknown part"));
        }
        String number = Validation.trimToNull(command.partNumber());
        if (number == null) {
            throw ValidationException.of("partId", ViolationCodes.REQUIRED, "partId or partNumber required");
        }
        try {
            return parts.findByPartNumber(new PartNumber(number))
                    .map(Part::id)
                    .orElseThrow(() ->
                            ValidationException.of("partNumber", ViolationCodes.UNKNOWN_REFERENCE, "unknown part"));
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of("partNumber", ViolationCodes.INVALID_FORMAT, "invalid part number");
        }
    }

    private Relation requireRelation(UUID relationId) {
        return relations
                .findById(relationId)
                .orElseThrow(() -> new NotFoundException(MasterdataType.RELATION.label(), relationId));
    }

    private PartMapping getInRelation(UUID relationId, UUID id) {
        requireRelation(relationId);
        PartMapping mapping = get(id);
        if (!mapping.values().relationId().equals(relationId)) {
            throw new NotFoundException(type().label(), id);
        }
        return mapping;
    }

    @Override
    protected void checkUnique(PartMapping self, PartMappingValues values) {
        mappings.findByRelationAndPartnerPartNumber(values.relationId(), values.partnerPartNumber())
                .filter(other -> self == null || !other.id().equals(self.id()))
                .ifPresent(other -> {
                    throw new DuplicateKeyException(type().label(), "partnerPartNumber", values.partnerPartNumber());
                });
    }

    @Override
    protected List<FieldViolation> warnings(PartMappingValues values) {
        var warnings = new ArrayList<FieldViolation>();
        checkSite(warnings, "ownSiteBpns", values.ownSiteBpns());
        checkSite(warnings, "partnerSiteBpns", values.partnerSiteBpns());
        parts.findById(values.partId())
                .filter(part -> !part.active())
                .ifPresent(part -> warnings.add(
                        new FieldViolation("partNumber", ViolationCodes.INACTIVE_REFERENCE, "part is inactive")));
        return warnings;
    }

    private void checkSite(List<FieldViolation> warnings, String field, Bpns bpns) {
        if (bpns != null && sites.findByBpns(bpns).isEmpty()) {
            warnings.add(new FieldViolation(field, ViolationCodes.UNKNOWN_REFERENCE, "unknown site"));
        }
    }

    private PartMappingView view(PartMapping mapping) {
        return views(List.of(mapping)).getFirst();
    }

    private List<PartMappingView> views(List<PartMapping> list) {
        Map<UUID, Part> byId =
                parts.findAllById(list.stream().map(m -> m.values().partId()).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Part::id, Function.identity()));
        return list.stream()
                .map(m -> {
                    Part part = byId.get(m.values().partId());
                    return new PartMappingView(
                            m,
                            part == null ? null : part.businessKey(),
                            part == null ? null : part.values().nameDe());
                })
                .toList();
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.PART_MAPPING;
    }

    @Override
    protected String keyField() {
        return "partId";
    }

    @Override
    protected Object keyOf(PartMappingValues values) {
        return values.relationId() + "/" + values.partId();
    }

    @Override
    protected Optional<PartMapping> findById(UUID id) {
        return mappings.findById(id);
    }

    @Override
    protected Optional<PartMapping> findByKey(PartMappingValues values) {
        return mappings.findByRelationAndPart(values.relationId(), values.partId());
    }

    @Override
    protected PartMapping newAggregate(PartMappingValues values, boolean active) {
        return PartMapping.create(values);
    }

    @Override
    protected PartMapping persist(PartMapping aggregate) {
        return mappings.save(aggregate);
    }

    @Override
    protected void remove(PartMapping aggregate) {
        mappings.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(PartMapping aggregate) {
        return false;
    }
}
