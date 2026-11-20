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
 * @param averageLeadTimeDays average days from request to the last decision of closed (approved) cases decided since
 *     {@code Parameters.leadTimeSince}, {@code null} without data
 * @param approvalRate share of approvals (incl. conditional) among all decided revisions (0..1), {@code null} without
 *     decisions
 * @param byStatus number of cases per status
 * @param byPartner open and overdue cases per partner
 * @param needsClarification received requests waiting for clarification (UC-04 A1)
 * @param blockingChildren open cases with sub-supplier requests that are not decided yet (UC-09)
 */
public record CaseStatistics(
        long open,
        long overdue,
        long inReview,
        long approvedLast30Days,
        Double averageLeadTimeDays,
        Double approvalRate,
        Map<String, Long> byStatus,
        List<PartnerFigures> byPartner,
        long needsClarification,
        long blockingChildren) {

    /**
     * Figures of one partner.
     *
     * @param exchangeHealth {@code OK}, {@code DEGRADED} or {@code FAILING} (last 24 h), {@code null} if unknown
     * @param lastExchangeAt last message exchanged with the partner, {@code null} if none
     */
    public record PartnerFigures(
            String bpnl, String name, long open, long overdue, String exchangeHealth, Instant lastExchangeAt) {

        public PartnerFigures(String bpnl, String name, long open, long overdue) {
            this(bpnl, name, open, overdue, null, null);
        }

        /** Copy with the exchange health of the partner. */
        public PartnerFigures withExchange(String health, Instant lastExchange) {
            return new PartnerFigures(bpnl, name, open, overdue, health, lastExchange);
        }
    }

    /** Copy with other partner figures. */
    public CaseStatistics withPartners(List<PartnerFigures> partners) {
        return new CaseStatistics(
                open,
                overdue,
                inReview,
                approvedLast30Days,
                averageLeadTimeDays,
                approvalRate,
                byStatus,
                partners,
                needsClarification,
                blockingChildren);
    }

    /**
     * Reference values.
     *
     * @param since start of the "last 30 days" window
     * @param leadTimeSince start of the lead time window (closed cases of the last 12 months)
     */
    public record Parameters(LocalDate today, Instant since, Instant leadTimeSince) {

        public Parameters(LocalDate today, Instant since) {
            this(today, since, since.minus(java.time.Duration.ofDays(335)));
        }
    }
}
