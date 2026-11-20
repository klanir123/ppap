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
 * @param parent parent case of a sub-supplier request, {@code null} otherwise (phase 6, UC-09)
 * @param children sub-supplier requests of a supplier case (phase 6, UC-09)
 * @param actorDisplays display name per recorded timeline actor (phase 6, UC-11)
 */
public record CaseView(
        ApprovalCase approvalCase,
        List<CaseAction> allowedActions,
        LocalDate today,
        Completeness draftCompleteness,
        CaseExchangeStatus.LastOutbound lastOutbound,
        Map<String, DocumentTransferStatus.Transfer> transfers,
        CascadeQueries.ParentRef parent,
        List<CascadeQueries.ChildRef> children,
        Map<String, String> actorDisplays) {

    public CaseView {
        transfers = transfers == null ? Map.of() : Map.copyOf(transfers);
        children = children == null ? List.of() : List.copyOf(children);
        actorDisplays = actorDisplays == null ? Map.of() : Map.copyOf(actorDisplays);
    }

    public CaseView(
            ApprovalCase approvalCase,
            List<CaseAction> allowedActions,
            LocalDate today,
            Completeness draftCompleteness) {
        this(approvalCase, allowedActions, today, draftCompleteness, null, Map.of(), null, List.of(), Map.of());
    }

    /** Display name of a timeline actor. */
    public String actorDisplay(String actor) {
        return actorDisplays.getOrDefault(actor, actor);
    }
}
