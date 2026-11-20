package com.msg.ppap.approval.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw data of the usage counters (UC-12): submitted revisions of the tenant – submitted by the own tenant as supplier
 * or received from a supplier as customer. Used by the usage module (event-based counting and recomputation).
 */
public interface RevisionFactsUseCase {

    /** All submitted revisions, oldest first. */
    List<RevisionFact> submittedRevisions();

    /** One submitted revision of a case. */
    Optional<RevisionFact> submittedRevision(UUID caseId, int revision);

    /**
     * A submitted revision.
     *
     * @param ownRole {@code SUPPLIER} (submitted by the own tenant) or {@code CUSTOMER} (received)
     * @param source origin of the case ({@code LEGACY_IMPORT} cases are not counted)
     * @param submittedAt time of the submission (signed in the payload)
     */
    record RevisionFact(
            UUID caseId, UUID exchangeId, String ownRole, String source, int revision, Instant submittedAt) {}
}
