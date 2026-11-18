package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerRole;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.masterdata.domain.RelationValues;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ReferencedException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for customer-supplier relations (UC-01). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class RelationService extends MasterdataService<Relation, RelationValues> {

    private final RelationRepository relations;
    private final PartnerRepository partners;
    private final PartMappingRepository mappings;

    public RelationService(
            RelationRepository relations,
            PartnerRepository partners,
            PartMappingRepository mappings,
            MasterdataEvents events) {
        super(events);
        this.relations = relations;
        this.partners = partners;
        this.mappings = mappings;
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public RelationView view(UUID id) {
        return view(get(id));
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Page<RelationView> search(
            String text, Boolean active, String partnerBpnl, String partnerRole, Pageable pageable) {
        Page<Relation> page = relations.search(
                Keys.text(text),
                active,
                Keys.optionalBpnl("partnerBpnl", partnerBpnl),
                Keys.optionalEnum("partnerRole", partnerRole, PartnerRole.class),
                pageable);
        return new PageImpl<>(views(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /** Finds the relation for a partner and role or fails with {@code NOT_FOUND}. */
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Relation findByPartner(String partnerBpnl, String partnerRole) {
        Bpnl bpnl = Keys.optionalBpnl("partnerBpnl", partnerBpnl);
        PartnerRole role = Keys.optionalEnum("partnerRole", partnerRole, PartnerRole.class);
        if (bpnl == null || role == null) {
            throw ValidationException.of(
                    bpnl == null ? "partnerBpnl" : "partnerRole", ViolationCodes.REQUIRED, "must not be blank");
        }
        return relations
                .findByPartner(bpnl, role)
                .orElseThrow(() -> new NotFoundException(type().label(), bpnl + "/" + role));
    }

    public RelationView create(RelationCommand command) {
        return view(doCreate(toValues(command), command.active()));
    }

    public RelationView update(UUID id, RelationCommand command, Long expectedVersion) {
        return view(doUpdate(id, toValues(command), command.active(), expectedVersion));
    }

    public UpsertResult<Relation> upsert(RelationCommand command) {
        return doUpsert(toValues(command), command.active(), null);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PlanResult plan(RelationCommand command) {
        return doPlan(toValues(command), command.active());
    }

    private RelationValues toValues(RelationCommand command) {
        RelationValues values = command.toValues();
        Partner partner = partners.findByBpnl(values.partnerBpnl())
                .orElseThrow(() ->
                        ValidationException.of("partnerBpnl", ViolationCodes.UNKNOWN_REFERENCE, "unknown partner"));
        if (!partner.hasRole(values.partnerRole())) {
            throw ValidationException.of(
                    "partnerRole",
                    ViolationCodes.ROLE_MISMATCH,
                    "partner does not have the role " + values.partnerRole());
        }
        return values;
    }

    @Override
    protected List<FieldViolation> warnings(RelationValues values) {
        var warnings = new ArrayList<FieldViolation>();
        partners.findByBpnl(values.partnerBpnl()).ifPresent(partner -> {
            if (!partner.active()) {
                warnings.add(
                        new FieldViolation("partnerBpnl", ViolationCodes.INACTIVE_REFERENCE, "partner is inactive"));
            } else if (!partner.exchangeReady()) {
                warnings.add(new FieldViolation(
                        "partnerBpnl", ViolationCodes.NOT_EXCHANGE_READY, "partner lacks DSP endpoint or DID"));
            }
        });
        return warnings;
    }

    private RelationView view(Relation relation) {
        return views(List.of(relation)).getFirst();
    }

    private List<RelationView> views(List<Relation> list) {
        Map<Bpnl, String> names =
                partners
                        .findAllByBpnl(
                                list.stream().map(r -> r.values().partnerBpnl()).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(
                                p -> p.values().bpnl(), p -> p.values().name(), (a, b) -> a));
        Map<UUID, Long> counts =
                mappings.countByRelations(list.stream().map(Relation::id).collect(Collectors.toSet()));
        return list.stream()
                .map(r -> new RelationView(r, names.get(r.values().partnerBpnl()), counts.getOrDefault(r.id(), 0L)))
                .toList();
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.RELATION;
    }

    @Override
    protected String keyField() {
        return "partnerRole";
    }

    @Override
    protected Object keyOf(RelationValues values) {
        return values.key();
    }

    @Override
    protected Optional<Relation> findById(UUID id) {
        return relations.findById(id);
    }

    @Override
    protected Optional<Relation> findByKey(RelationValues values) {
        return relations.findByPartner(values.partnerBpnl(), values.partnerRole());
    }

    @Override
    protected Relation newAggregate(RelationValues values, boolean active) {
        return Relation.create(values, active);
    }

    @Override
    protected Relation persist(Relation aggregate) {
        return relations.save(aggregate);
    }

    @Override
    protected void remove(Relation aggregate) {
        relations.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(Relation aggregate) {
        return mappings.existsByRelation(aggregate.id());
    }

    @Override
    protected void checkKeyChange(Relation existing, RelationValues newValues) {
        if (isReferenced(existing)) {
            throw new ReferencedException("Partner or role of relation '" + existing.businessKey()
                    + "' cannot change: the relation has part mappings");
        }
    }
}
