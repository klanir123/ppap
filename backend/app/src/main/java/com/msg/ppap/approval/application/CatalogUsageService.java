package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.out.ApprovalCaseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers whether approval cases reference master data (parts, partners, relations, catalogue codes), so that master
 * data referenced by cases is only deactivated, never deleted physically.
 */
@Service
@Transactional(readOnly = true)
public class CatalogUsageService {

    private final ApprovalCaseRepository repository;

    public CatalogUsageService(ApprovalCaseRepository repository) {
        this.repository = repository;
    }

    public boolean isPartUsed(UUID partId) {
        return repository.existsByPart(partId);
    }

    public boolean isPartnerUsed(String bpnl) {
        return repository.existsByPartner(bpnl);
    }

    public boolean isRelationUsed(UUID relationId) {
        return repository.existsByRelation(relationId);
    }

    public boolean isElementUsed(String code) {
        return repository.existsByElement(code);
    }

    public boolean isLevelUsed(String code) {
        return repository.existsByLevel(code);
    }

    public boolean isReasonUsed(String code) {
        return repository.existsByReason(code);
    }
}
