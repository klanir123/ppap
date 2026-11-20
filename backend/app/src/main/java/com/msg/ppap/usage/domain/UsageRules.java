package com.msg.ppap.usage.domain;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

/** Counting rules of UC-12 (pure functions, unit-tested). */
public final class UsageRules {

    /** Cases migrated from earlier systems are not counted. */
    public static final String LEGACY_IMPORT = "LEGACY_IMPORT";

    private UsageRules() {}

    /**
     * Metric of a submitted revision.
     *
     * @param ownRole {@code SUPPLIER} (submitted) or {@code CUSTOMER} (received)
     * @param source origin of the case
     * @return empty for legacy cases or unknown roles
     */
    public static Optional<UsageMetric> metric(String ownRole, String source, int revision) {
        if (LEGACY_IMPORT.equals(source) || revision < 1) {
            return Optional.empty();
        }
        boolean first = revision == 1;
        return switch (ownRole) {
            case "SUPPLIER" ->
                Optional.of(first ? UsageMetric.APPROVAL_CASES_SUBMITTED : UsageMetric.REVISIONS_SUBMITTED);
            case "CUSTOMER" ->
                Optional.of(first ? UsageMetric.APPROVAL_CASES_RECEIVED : UsageMetric.REVISIONS_RECEIVED);
            default -> Optional.empty();
        };
    }

    /** Month (UTC) of a submission, {@code YYYY-MM}. */
    public static String month(Instant at) {
        return YearMonth.from(at.atZone(ZoneOffset.UTC)).toString();
    }

    /** Parses {@code YYYY-MM}. */
    public static YearMonth parseMonth(String value) {
        if (value == null || !value.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("YYYY-MM expected: " + value);
        }
        return YearMonth.parse(value);
    }
}
