package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartValues;
import com.msg.ppap.platform.domain.PpapException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for own parts (UC-01). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class PartService extends MasterdataService<Part, PartValues> {

    private final PartRepository parts;
    private final PartMappingRepository mappings;

    public PartService(PartRepository parts, PartMappingRepository mappings, MasterdataEvents events) {
        super(events);
        this.parts = parts;
        this.mappings = mappings;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Part get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Page<Part> search(String text, Boolean active, String partnerBpnl, Pageable pageable) {
        return parts.search(Keys.text(text), active, Keys.optionalBpnl("partnerBpnl", partnerBpnl), pageable);
    }

    public Part create(PartCommand command) {
        return doCreate(command.toValues(), command.active());
    }

    public Part update(UUID id, PartCommand command, Long expectedVersion) {
        return doUpdate(id, command.toValues(), command.active(), expectedVersion);
    }

    /** {@code PUT /parts/by-number/{partNumber}}. */
    public UpsertResult<Part> upsertByPartNumber(String partNumber, PartCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("partNumber", partNumber, command.partNumber());
        var withKey = new PartCommand(
                partNumber,
                command.nameDe(),
                command.nameEn(),
                command.drawingNumber(),
                command.drawingRevision(),
                command.material(),
                command.catenaXId(),
                command.active());
        return doUpsert(withKey.toValues(), command.active(), expectedVersion);
    }

    /** Upsert by the part number in the command (import). */
    public UpsertResult<Part> upsert(PartCommand command) {
        return doUpsert(command.toValues(), command.active(), null);
    }

    /** Dry run of {@link #upsert}. */
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PlanResult plan(PartCommand command) {
        return doPlan(command.toValues(), command.active());
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.PART;
    }

    @Override
    protected String keyField() {
        return "partNumber";
    }

    @Override
    protected Object keyOf(PartValues values) {
        return values.partNumber();
    }

    @Override
    protected Optional<Part> findById(UUID id) {
        return parts.findById(id);
    }

    @Override
    protected Optional<Part> findByKey(PartValues values) {
        return parts.findByPartNumber(values.partNumber());
    }

    @Override
    protected Part newAggregate(PartValues values, boolean active) {
        return Part.create(values, active);
    }

    @Override
    protected Part persist(Part aggregate) {
        return parts.save(aggregate);
    }

    @Override
    protected void remove(Part aggregate) {
        parts.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(Part aggregate) {
        // TODO(phase-3): also referenced by approval cases
        return mappings.existsByPart(aggregate.id());
    }
}
