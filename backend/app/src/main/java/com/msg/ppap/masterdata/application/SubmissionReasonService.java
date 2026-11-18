package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.port.out.CatalogRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import com.msg.ppap.masterdata.domain.SubmissionReasonValues;
import com.msg.ppap.platform.domain.PpapException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for submission reasons (UC-02). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class SubmissionReasonService extends MasterdataService<SubmissionReason, SubmissionReasonValues> {

    private final CatalogRepository catalog;

    public SubmissionReasonService(CatalogRepository catalog, MasterdataEvents events) {
        super(events);
        this.catalog = catalog;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public SubmissionReason get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public List<SubmissionReason> list(Boolean active) {
        return catalog.findReasons(active);
    }

    public SubmissionReason create(SubmissionReasonCommand command) {
        return doCreate(command.toValues(), command.active());
    }

    public SubmissionReason update(UUID id, SubmissionReasonCommand command, Long expectedVersion) {
        return doUpdate(id, command.toValues(), command.active(), expectedVersion);
    }

    public UpsertResult<SubmissionReason> upsertByCode(
            String code, SubmissionReasonCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("code", code, command.code());
        return doUpsert(command.withCode(code).toValues(), command.active(), expectedVersion);
    }

    public UpsertResult<SubmissionReason> upsert(SubmissionReasonCommand command) {
        return doUpsert(command.toValues(), command.active(), null);
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.SUBMISSION_REASON;
    }

    @Override
    protected String keyField() {
        return "code";
    }

    @Override
    protected Object keyOf(SubmissionReasonValues values) {
        return values.code();
    }

    @Override
    protected Optional<SubmissionReason> findById(UUID id) {
        return catalog.findReasonById(id);
    }

    @Override
    protected Optional<SubmissionReason> findByKey(SubmissionReasonValues values) {
        return catalog.findReasonByCode(values.code());
    }

    @Override
    protected SubmissionReason newAggregate(SubmissionReasonValues values, boolean active) {
        return SubmissionReason.create(values, active);
    }

    @Override
    protected SubmissionReason persist(SubmissionReason aggregate) {
        return catalog.save(aggregate);
    }

    @Override
    protected void remove(SubmissionReason aggregate) {
        catalog.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(SubmissionReason aggregate) {
        return false; // TODO(phase-3): referenced by approval cases
    }
}
