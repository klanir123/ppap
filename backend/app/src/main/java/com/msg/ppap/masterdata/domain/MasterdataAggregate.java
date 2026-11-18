package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.VersionConflictException;
import java.util.Objects;
import java.util.UUID;

/**
 * Common behaviour of master data aggregates: a technical id, validated values (whose business key identifies the
 * object within the tenant), an active flag (soft delete) and technical metadata.
 *
 * @param <V> immutable, validated value type
 */
public abstract class MasterdataAggregate<V> {

    private final UUID id;
    private V values;
    private boolean active;
    private final Metadata metadata;

    protected MasterdataAggregate(UUID id, V values, boolean active, Metadata metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.values = Objects.requireNonNull(values, "values");
        this.active = active;
        this.metadata = metadata;
    }

    /** Object type used in audit events and error messages. */
    public abstract MasterdataType type();

    /** Business key, unique within the tenant. */
    public abstract String businessKey();

    public UUID id() {
        return id;
    }

    public V values() {
        return values;
    }

    public boolean active() {
        return active;
    }

    /** Metadata, {@code null} for objects not yet persisted. */
    public Metadata metadata() {
        return metadata;
    }

    /** Current version, or {@code null} for objects not yet persisted. */
    public Long version() {
        return metadata == null ? null : metadata.version();
    }

    /** Whether applying the given state would modify the object. */
    public boolean wouldChange(V newValues, boolean newActive) {
        return !values.equals(newValues) || active != newActive;
    }

    /** Replaces values and active flag. */
    public void update(V newValues, boolean newActive) {
        this.values = Objects.requireNonNull(newValues, "values");
        this.active = newActive;
    }

    /** Soft delete. */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Optimistic locking check against a client-supplied version ({@code If-Match}).
     *
     * @param expectedVersion expected version or {@code null} (last-write-wins)
     * @throws VersionConflictException if the versions differ
     */
    public void checkVersion(Long expectedVersion) {
        if (expectedVersion != null && metadata != null && expectedVersion != metadata.version()) {
            throw new VersionConflictException(type().label(), id, expectedVersion, metadata.version());
        }
    }
}
