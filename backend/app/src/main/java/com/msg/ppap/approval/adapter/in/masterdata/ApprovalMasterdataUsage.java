package com.msg.ppap.approval.adapter.in.masterdata;

import com.msg.ppap.approval.application.CatalogUsageService;
import com.msg.ppap.masterdata.api.MasterdataUsage;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements the master data extension point {@link MasterdataUsage}: master data referenced by approval cases is
 * deactivated instead of deleted. Dependency direction: approval → masterdata API only (no cycle).
 */
@Component
class ApprovalMasterdataUsage implements MasterdataUsage {

    private final CatalogUsageService usage;

    ApprovalMasterdataUsage(CatalogUsageService usage) {
        this.usage = usage;
    }

    @Override
    public boolean isPartUsed(UUID partId) {
        return usage.isPartUsed(partId);
    }

    @Override
    public boolean isPartnerUsed(String bpnl) {
        return usage.isPartnerUsed(bpnl);
    }

    @Override
    public boolean isRelationUsed(UUID relationId) {
        return usage.isRelationUsed(relationId);
    }

    @Override
    public boolean isElementUsed(String code) {
        return usage.isElementUsed(code);
    }

    @Override
    public boolean isLevelUsed(String code) {
        return usage.isLevelUsed(code);
    }

    @Override
    public boolean isReasonUsed(String code) {
        return usage.isReasonUsed(code);
    }
}
