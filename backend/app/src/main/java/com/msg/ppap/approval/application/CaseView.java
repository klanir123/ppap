package com.msg.ppap.approval.application;

import com.msg.ppap.approval.CaseExchangeStatus;
import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.Completeness;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A case with the actions allowed for the current actor.
 *
 * @param draftCompleteness completeness of the draft revision, {@code null} without draft
 * @param lastOutbound latest outbound partner message, {@code null} if none (or exchange not available)
 * @param transfers transfer state of partner documents by SHA-256 (phase 5)
 */
public record CaseView(
        ApprovalCase approvalCase,
        List<CaseAction> allowedActions,
        LocalDate today,
        Completeness draftCompleteness,
        CaseExchangeStatus.LastOutbound lastOutbound,
        Map<String, DocumentTransferStatus.Transfer> transfers) {

    public CaseView {
        transfers = transfers == null ? Map.of() : Map.copyOf(transfers);
    }

    public CaseView(
            ApprovalCase approvalCase,
            List<CaseAction> allowedActions,
            LocalDate today,
            Completeness draftCompleteness) {
        this(approvalCase, allowedActions, today, draftCompleteness, null, Map.of());
    }
}
