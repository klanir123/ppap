package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.MasterdataChanged.Action;
import com.msg.ppap.masterdata.domain.MasterdataAggregate;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.platform.domain.DuplicateKeyException;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared use case logic of master data aggregates: create, update (optimistic locking), upsert by business key
 * (idempotent), dry-run planning for imports and delete (physical only if never referenced, otherwise deactivate).
 * Every write publishes a {@link com.msg.ppap.masterdata.MasterdataChanged} event.
 *
 * <p>Subclasses validate commands into values (including references to other aggregates) and provide persistence.
 *
 * @param <A> aggregate type
 * @param <V> value type
 */
// Inherited public methods (e.g. delete) need their own annotation: a subclass' class-level @Transactional does not
// cover methods declared here.
@Transactional(noRollbackFor = PpapException.class)
public abstract class MasterdataService<A extends MasterdataAggregate<V>, V> {

    private final MasterdataEvents events;

    protected MasterdataService(MasterdataEvents events) {
        this.events = events;
    }

    protected abstract MasterdataType type();

    /** Name of the business key field (for duplicate key errors). */
    protected abstract String keyField();

    /** Business key value of the given values (for duplicate key errors). */
    protected abstract Object keyOf(V values);

    protected abstract Optional<A> findById(UUID id);

    protected abstract Optional<A> findByKey(V values);

    protected abstract A newAggregate(V values, boolean active);

    /** Saves and flushes, returning the persisted state. */
    protected abstract A persist(A aggregate);

    protected abstract void remove(A aggregate);

    /** Whether other objects reference the aggregate (then delete only deactivates). */
    protected abstract boolean isReferenced(A aggregate);

    /** Additional uniqueness checks (e.g. secondary keys); {@code self} is {@code null} on create. */
    protected void checkUnique(A self, V values) {}

    /** Checks whether the business key of an existing aggregate may change (e.g. not if referenced by key). */
    protected void checkKeyChange(A existing, V newValues) {}

    /** Non-blocking findings shown in import previews. */
    protected List<FieldViolation> warnings(V values) {
        return List.of();
    }

    /** Loads an aggregate or fails with {@code NOT_FOUND}. */
    public A get(UUID id) {
        return findById(id).orElseThrow(() -> new NotFoundException(type().label(), id));
    }

    /** Creates a new aggregate; fails with {@code DUPLICATE_KEY} if the business key exists. */
    protected A doCreate(V values, Boolean active) {
        findByKey(values).ifPresent(existing -> {
            throw new DuplicateKeyException(type().label(), keyField(), keyOf(values));
        });
        checkUnique(null, values);
        A saved = persist(newAggregate(values, active == null || active));
        events.changed(saved, Action.CREATED);
        return saved;
    }

    /** Updates an existing aggregate by id. {@code active == null} keeps the current state. */
    protected A doUpdate(UUID id, V values, Boolean active, Long expectedVersion) {
        A existing = get(id);
        existing.checkVersion(expectedVersion);
        return apply(existing, values, active).value();
    }

    /** Upsert by business key: updates the existing aggregate or creates a new one. */
    protected UpsertResult<A> doUpsert(V values, Boolean active, Long expectedVersion) {
        Optional<A> existing = findByKey(values);
        if (existing.isPresent()) {
            existing.get().checkVersion(expectedVersion);
            return apply(existing.get(), values, active);
        }
        return new UpsertResult<>(doCreate(values, active), ChangeAction.CREATED);
    }

    /** Dry run of {@link #doUpsert}: determines the action without writing. */
    protected PlanResult doPlan(V values, Boolean active) {
        Optional<A> existing = findByKey(values);
        ChangeAction action;
        if (existing.isEmpty()) {
            checkUnique(null, values);
            action = ChangeAction.CREATED;
        } else {
            A current = existing.get();
            checkUnique(current, values);
            action = current.wouldChange(values, active == null ? current.active() : active)
                    ? ChangeAction.UPDATED
                    : ChangeAction.UNCHANGED;
        }
        return new PlanResult(action, warnings(values));
    }

    /**
     * Deletes the aggregate physically if it has never been referenced, otherwise deactivates it.
     *
     * @return {@code true} if deleted physically
     */
    public boolean delete(UUID id, Long expectedVersion) {
        A existing = get(id);
        existing.checkVersion(expectedVersion);
        if (isReferenced(existing)) {
            if (existing.active()) {
                existing.deactivate();
                events.changed(persist(existing), Action.DEACTIVATED);
            }
            return false;
        }
        remove(existing);
        events.changed(existing, Action.DELETED);
        return true;
    }

    private UpsertResult<A> apply(A existing, V values, Boolean active) {
        boolean newActive = active == null ? existing.active() : active;
        if (!Objects.equals(keyOf(existing.values()), keyOf(values))) {
            findByKey(values).filter(other -> !other.id().equals(existing.id())).ifPresent(other -> {
                throw new DuplicateKeyException(type().label(), keyField(), keyOf(values));
            });
            checkKeyChange(existing, values);
        }
        checkUnique(existing, values);
        if (!existing.wouldChange(values, newActive)) {
            return new UpsertResult<>(existing, ChangeAction.UNCHANGED);
        }
        boolean deactivated = existing.active() && !newActive;
        existing.update(values, newActive);
        A saved = persist(existing);
        events.changed(saved, deactivated ? Action.DEACTIVATED : Action.UPDATED);
        return new UpsertResult<>(saved, ChangeAction.UPDATED);
    }
}
