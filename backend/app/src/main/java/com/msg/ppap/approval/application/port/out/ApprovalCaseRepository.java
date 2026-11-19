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
}
