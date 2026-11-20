package com.msg.ppap.approval.application.port.in;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot of an approval case for the audit export (UC-11): case header, timeline, submitted revisions (hash,
 * signature) and decisions as JSON-compatible values. Used by the audit module.
 */
public interface CaseExportUseCase {

    /**
     * Export snapshot of a case of the tenant.
     *
     * @return {@code case}, {@code timeline}, {@code submissions}, {@code decisions} and the case number under {@code
     *     caseNumber}; empty if the case does not exist
     */
    Optional<Map<String, Object>> export(UUID caseId);
}
