package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.Completeness;
import java.time.LocalDate;
import java.util.List;

/**
 * A case with the actions allowed for the current actor.
 *
 * @param draftCompleteness completeness of the draft revision, {@code null} without draft
 * @param lastOutbound latest outbound partner message, {@code null} if none (or exchange not available)
 */
public record CaseView(
        ApprovalCase approvalCase,
        List<CaseAction> allowedActions,
        LocalDate today,
        Completeness draftCompleteness,
        com.msg.ppap.approval.CaseExchangeStatus.LastOutbound lastOutbound) {

    public CaseView(
            ApprovalCase approvalCase,
            List<CaseAction> allowedActions,
            LocalDate today,
            Completeness draftCompleteness) {
        this(approvalCase, allowedActions, today, draftCompleteness, null);
    }
}
