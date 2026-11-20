package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.out.CaseSummary;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.TimelineEntry;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Read side of the cascade (UC-09) and of the actor display (UC-11) for case views: parent and child cases,
 * completeness hint {@code SUB_SUPPLIER_PENDING} and partner names instead of {@code partner:<BPNL>}.
 */
@Component
public class CascadeQueries {

    /** Statuses of a child case that is not decided yet (blocks nothing, but is reported as hint). */
    static final Set<CaseStatus> PENDING = EnumSet.of(
            CaseStatus.DRAFT, CaseStatus.REQUESTED, CaseStatus.IN_PROGRESS, CaseStatus.SUBMITTED, CaseStatus.IN_REVIEW);

    private final ApprovalContext context;
    private final MasterdataQueries masterdata;

    public CascadeQueries(ApprovalContext context, MasterdataQueries masterdata) {
        this.context = context;
        this.masterdata = masterdata;
    }

    /** Parent case of a sub-supplier request. */
    public record ParentRef(UUID id, String caseNumber, PartnerRef partner) {}

    /** Child case (sub-supplier request) of a case. */
    public record ChildRef(
            UUID id,
            String caseNumber,
            PartnerRef partner,
            String partNumber,
            CaseStatus status,
            DecisionResult lastDecisionResult) {}

    /** Parent of the case, if it is a sub-supplier request. */
    public Optional<ParentRef> parent(ApprovalCase c) {
        if (c.parentCaseId() == null) {
            return Optional.empty();
        }
        return context.repository()
                .findById(c.parentCaseId())
                .map(p -> new ParentRef(p.id(), p.caseNumber(), p.header().partner()));
    }

    /** Child cases of a supplier case (empty for others). */
    public List<CaseSummary> childSummaries(ApprovalCase c) {
        return c.ownRole() == CaseRole.SUPPLIER ? context.repository().findChildren(c.id()) : List.of();
    }

    /** Child cases as references. */
    public List<ChildRef> children(ApprovalCase c) {
        return childSummaries(c).stream()
                .map(s -> new ChildRef(
                        s.id(),
                        s.caseNumber(),
                        s.partner(),
                        s.part() == null ? null : s.part().partNumber(),
                        s.status(),
                        s.lastDecision()))
                .toList();
    }

    /** Hints of the completeness check: one {@code SUB_SUPPLIER_PENDING} per child that is not decided yet. */
    public List<Completeness.Issue> pendingHints(List<CaseSummary> children) {
        return children.stream()
                .filter(child -> PENDING.contains(child.status()))
                .map(child -> new Completeness.Issue(
                        null,
                        Completeness.SUB_SUPPLIER_PENDING,
                        "Sub-supplier case " + child.caseNumber() + " ("
                                + child.partner().name() + ") is " + child.status() + ", not decided yet",
                        null))
                .toList();
    }

    /** Display names of all actors of the timeline (partner name instead of {@code partner:<BPNL>}). */
    public Map<String, String> actorDisplays(ApprovalCase c) {
        Map<String, String> displays = new LinkedHashMap<>();
        for (TimelineEntry entry : c.timeline()) {
            displays.computeIfAbsent(
                    entry.actor(), actor -> display(actor, c.header().partner()));
        }
        return displays;
    }

    /** Display name of one actor; the case partner's name is known without a master data lookup. */
    public String display(String actor, PartnerRef casePartner) {
        return Actor.display(actor, bpnl -> {
            if (casePartner != null && bpnl.equals(casePartner.bpnl()) && !bpnl.equals(casePartner.name())) {
                return Optional.of(casePartner.name());
            }
            return masterdata.findPartner(bpnl).map(MasterdataQueries.PartnerInfo::name);
        });
    }
}
