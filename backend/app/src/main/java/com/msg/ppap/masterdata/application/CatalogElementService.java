package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.CatalogRepository;
import com.msg.ppap.masterdata.application.port.out.ValueSchemaValidator;
import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.CatalogElementValues;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ReferencedException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for submission catalog elements (UC-02). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class CatalogElementService extends MasterdataService<CatalogElement, CatalogElementValues> {

    private final CatalogRepository catalog;
    private final ValueSchemaValidator schemaValidator;

    public CatalogElementService(
            CatalogRepository catalog, ValueSchemaValidator schemaValidator, MasterdataEvents events) {
        super(events);
        this.catalog = catalog;
        this.schemaValidator = schemaValidator;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public CatalogElement get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public List<CatalogElement> list(Boolean active) {
        return catalog.findElements(active);
    }

    public CatalogElement create(CatalogElementCommand command) {
        return doCreate(toValues(command), command.active());
    }

    public CatalogElement update(UUID id, CatalogElementCommand command, Long expectedVersion) {
        return doUpdate(id, toValues(command), command.active(), expectedVersion);
    }

    /** {@code PUT /catalog/elements/by-code/{code}}. */
    public UpsertResult<CatalogElement> upsertByCode(String code, CatalogElementCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("code", code, command.code());
        return doUpsert(toValues(command.withCode(code)), command.active(), expectedVersion);
    }

    public UpsertResult<CatalogElement> upsert(CatalogElementCommand command) {
        return doUpsert(toValues(command), command.active(), null);
    }

    private CatalogElementValues toValues(CatalogElementCommand command) {
        CatalogElementValues values = command.toValues();
        if (values.valueSchema() != null) {
            List<String> problems = schemaValidator.validate(values.valueSchema());
            if (!problems.isEmpty()) {
                throw new ValidationException(problems.stream()
                        .map(problem -> new FieldViolation(
                                "valueSchema",
                                ViolationCodes.INVALID_VALUE,
                                "not a valid JSON Schema (draft 2020-12): " + problem))
                        .toList());
            }
        }
        return values;
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.CATALOG_ELEMENT;
    }

    @Override
    protected String keyField() {
        return "code";
    }

    @Override
    protected Object keyOf(CatalogElementValues values) {
        return values.code();
    }

    @Override
    protected Optional<CatalogElement> findById(UUID id) {
        return catalog.findElementById(id);
    }

    @Override
    protected Optional<CatalogElement> findByKey(CatalogElementValues values) {
        return catalog.findElementByCode(values.code());
    }

    @Override
    protected CatalogElement newAggregate(CatalogElementValues values, boolean active) {
        return CatalogElement.create(values, active);
    }

    @Override
    protected CatalogElement persist(CatalogElement aggregate) {
        return catalog.save(aggregate);
    }

    @Override
    protected void remove(CatalogElement aggregate) {
        catalog.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(CatalogElement aggregate) {
        // TODO(phase-3): approval requests keep snapshots by value; a reference check is not needed there.
        return catalog.findLevels(null).stream()
                .anyMatch(level -> level.references(aggregate.values().code()));
    }

    @Override
    protected void checkKeyChange(CatalogElement existing, CatalogElementValues newValues) {
        if (isReferenced(existing)) {
            throw new ReferencedException(
                    "Code of element '" + existing.businessKey() + "' cannot change: used by submission levels");
        }
    }
}
