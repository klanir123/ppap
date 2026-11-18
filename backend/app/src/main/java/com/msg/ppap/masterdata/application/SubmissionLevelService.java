package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.CatalogRepository;
import com.msg.ppap.masterdata.domain.CatalogCode;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import com.msg.ppap.masterdata.domain.SubmissionLevelValues;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for submission levels and their requirement matrix (UC-02). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class SubmissionLevelService extends MasterdataService<SubmissionLevel, SubmissionLevelValues> {

    private final CatalogRepository catalog;

    public SubmissionLevelService(CatalogRepository catalog, MasterdataEvents events) {
        super(events);
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public SubmissionLevel get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public List<SubmissionLevel> list(Boolean active) {
        return catalog.findLevels(active);
    }

    public SubmissionLevel create(SubmissionLevelCommand command) {
        return doCreate(toValues(command), command.active());
    }

    /** Replaces the level including its complete requirement matrix. */
    public SubmissionLevel update(UUID id, SubmissionLevelCommand command, Long expectedVersion) {
        return doUpdate(id, toValues(command), command.active(), expectedVersion);
    }

    public UpsertResult<SubmissionLevel> upsertByCode(
            String code, SubmissionLevelCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("code", code, command.code());
        return doUpsert(toValues(command.withCode(code)), command.active(), expectedVersion);
    }

    public UpsertResult<SubmissionLevel> upsert(SubmissionLevelCommand command) {
        return doUpsert(toValues(command), command.active(), null);
    }

    private SubmissionLevelValues toValues(SubmissionLevelCommand command) {
        SubmissionLevelValues values = command.toValues();
        Set<CatalogCode> known = catalog.findElements(null).stream()
                .map(element -> element.values().code())
                .collect(Collectors.toSet());
        List<FieldViolation> unknown = values.requirements().keySet().stream()
                .filter(code -> !known.contains(code))
                .sorted((a, b) -> a.value().compareTo(b.value()))
                .map(code -> new FieldViolation(
                        "requirements." + code.value(), ViolationCodes.UNKNOWN_CODE, "unknown element code"))
                .toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException(unknown);
        }
        return values;
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.SUBMISSION_LEVEL;
    }

    @Override
    protected String keyField() {
        return "code";
    }

    @Override
    protected Object keyOf(SubmissionLevelValues values) {
        return values.code();
    }

    @Override
    protected Optional<SubmissionLevel> findById(UUID id) {
        return catalog.findLevelById(id);
    }

    @Override
    protected Optional<SubmissionLevel> findByKey(SubmissionLevelValues values) {
        return catalog.findLevelByCode(values.code());
    }

    @Override
    protected SubmissionLevel newAggregate(SubmissionLevelValues values, boolean active) {
        return SubmissionLevel.create(values, active);
    }

    @Override
    protected SubmissionLevel persist(SubmissionLevel aggregate) {
        return catalog.save(aggregate);
    }

    @Override
    protected void remove(SubmissionLevel aggregate) {
        catalog.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(SubmissionLevel aggregate) {
        return false; // TODO(phase-3): referenced by approval cases
    }
}
