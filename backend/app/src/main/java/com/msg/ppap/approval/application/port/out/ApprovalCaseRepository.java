package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of approval cases; scoped to the current tenant. */
public interface ApprovalCaseRepository {

    Optional<ApprovalCase> findById(UUID id);

    Optional<ApprovalCase> findByExchangeId(UUID exchangeId, CaseRole ownRole);

    boolean existsByCaseNumber(String caseNumber);

    /**
     * Saves the aggregate (new revisions, decisions and timeline entries are appended; submitted revisions and
     * decisions are never updated) and returns the persisted state with the new version.
     */
    ApprovalCase save(ApprovalCase approvalCase);

    /** Case list (read model without revisions). */
    Page<CaseSummary> search(CaseQuery query, Pageable pageable);

    /** Dashboard figures. */
    CaseStatistics statistics(CaseStatistics.Parameters parameters);

    boolean existsByPart(UUID partId);

    boolean existsByPartner(String bpnl);

    boolean existsByRelation(UUID relationId);

    boolean existsByLevel(String code);

    boolean existsByReason(String code);

    /** Whether a case requests the element (code in the request snapshot). */
    boolean existsByElement(String code);

    /**
     * Local document with the given SHA-256 shared with the partner: evidence of a submitted revision of a
     * dataspace-linked supplier case, or an attachment of a sent request of an own customer case.
     */
    java.util.Optional<UUID> findSharedDocument(String sha256, String partnerBpnl);

    /** Customer cases (source LOCAL) in the given statuses: id, case number, exchange id, partner, last revision. */
    java.util.List<CaseKey> findCustomerCases(java.util.Collection<String> statuses);

    /** Short key of a case. */
    record CaseKey(UUID id, String caseNumber, UUID exchangeId, String partnerBpnl, Integer currentRevision) {}

    /** Sub-supplier requests (child cases) of a case (UC-09), oldest first. */
    java.util.List<CaseSummary> findChildren(UUID parentId);

    /** Customer cases of the partner with a received revision whose signature is {@code UNVERIFIED}. */
    java.util.List<UUID> findCasesWithUnverifiedSignatures(String partnerBpnl);

    /** Latest timeline entries over all cases of the tenant (activity feed), newest first. */
    java.util.List<ActivityRow> recentActivity(int limit);

    /** A timeline entry with its case. */
    record ActivityRow(
            java.time.Instant at,
            UUID caseId,
            String caseNumber,
            String action,
            String actor,
            String fromStatus,
            String toStatus,
            String partnerBpnl,
            String partnerName) {}

    /** All submitted revisions of the tenant (raw data of the usage counters, UC-12). */
    java.util.List<RevisionRow> submittedRevisions();

    /** A submitted revision. */
    record RevisionRow(
            UUID caseId, UUID exchangeId, String ownRole, String source, int revision, java.time.Instant submittedAt) {}
}
