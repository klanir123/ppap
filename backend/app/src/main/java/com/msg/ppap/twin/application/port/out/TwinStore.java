package com.msg.ppap.twin.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Registered twins (table {@code tw_twin}); scoped to the current tenant. */
public interface TwinStore {

    /** Inserts or updates the registration (keeps the first registration time). */
    void save(UUID exchangeId, UUID caseId, String shellId, String shell, Instant at);

    Optional<StoredTwin> find(UUID exchangeId);

    /** A registered twin with the shell descriptor as read back from the DTR. */
    record StoredTwin(
            UUID exchangeId, UUID caseId, String shellId, String shell, Instant registeredAt, Instant updatedAt) {}
}
