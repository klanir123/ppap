package com.msg.ppap.approval.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard figures of the tenant.
 *
 * @param open open cases (REQUESTED, IN_PROGRESS, SUBMITTED, IN_REVIEW, REJECTED)
 * @param overdue open cases past their due date
 * @param inReview cases in review
 * @param approvedLast30Days approvals (incl. conditional) decided since {@code Parameters.since}
 * @param averageLeadTimeDays average days from request to final approval, {@code null} without data
 * @param approvalRate share of approvals among all decisions (0..1), {@code null} without decisions
 * @param byStatus number of cases per status
 * @param byPartner open and overdue cases per partner
 */
public record CaseStatistics(
        long open,
        long overdue,
        long inReview,
        long approvedLast30Days,
        Double averageLeadTimeDays,
        Double approvalRate,
        Map<String, Long> byStatus,
        List<PartnerFigures> byPartner) {

    /** Figures of one partner. */
    public record PartnerFigures(String bpnl, String name, long open, long overdue) {}

    /** Reference values. */
    public record Parameters(LocalDate today, Instant since) {}
}
