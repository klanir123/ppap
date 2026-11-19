package com.msg.ppap.approval.application.seed;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves seed dates: {@code ±Nd} relative to the reference time, or ISO-8601 dates / instants. */
final class SeedDates {

    private static final Pattern RELATIVE = Pattern.compile("^([+-]?)(\\d+)d$");

    private SeedDates() {}

    static LocalDate date(String raw, LocalDate today) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = RELATIVE.matcher(raw.strip());
        if (m.matches()) {
            long days = Long.parseLong(m.group(2));
            return "-".equals(m.group(1)) ? today.minusDays(days) : today.plusDays(days);
        }
        String value = raw.strip();
        return value.length() > 10 ? LocalDate.ofInstant(Instant.parse(value), ZoneOffset.UTC) : LocalDate.parse(value);
    }

    static Instant instant(String raw, Instant now) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = RELATIVE.matcher(raw.strip());
        if (m.matches()) {
            long days = Long.parseLong(m.group(2));
            return ("-".equals(m.group(1)) ? now.minus(days, ChronoUnit.DAYS) : now.plus(days, ChronoUnit.DAYS))
                    .truncatedTo(ChronoUnit.MILLIS);
        }
        String value = raw.strip();
        return value.length() > 10
                ? Instant.parse(value)
                : LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
