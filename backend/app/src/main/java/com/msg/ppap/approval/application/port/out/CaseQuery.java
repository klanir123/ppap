package com.msg.ppap.approval.application.port.out;

import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseStatus;
import java.time.LocalDate;
import java.util.Set;

/**
 * Filter of the case list; {@code null}/empty values do not filter.
 *
 * @param text full text over case number, part numbers, part name and partner name
 * @param overdue only open cases past their due date ({@code true}) or the others ({@code false})
 * @param today reference date for {@code overdue}
 * @param needsClarification only cases with an open clarification ({@code true}) or without ({@code false})
 */
public record CaseQuery(
        String text,
        Set<CaseStatus> statuses,
        CaseRole ownRole,
        String partnerBpnl,
        Boolean overdue,
        LocalDate today,
        Boolean needsClarification) {

    public CaseQuery {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }

    public CaseQuery(
            String text,
            Set<CaseStatus> statuses,
            CaseRole ownRole,
            String partnerBpnl,
            Boolean overdue,
            LocalDate today) {
        this(text, statuses, ownRole, partnerBpnl, overdue, today, null);
    }
}
