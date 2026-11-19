package com.msg.ppap.approval.application.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SeedDatesTest {

    static final LocalDate TODAY = LocalDate.parse("2026-11-19");
    static final Instant NOW = Instant.parse("2026-11-19T10:00:00.123456Z");

    @Test
    void resolvesRelativeAndAbsoluteDates() {
        assertThat(SeedDates.date("+21d", TODAY)).isEqualTo("2026-12-10");
        assertThat(SeedDates.date("-5d", TODAY)).isEqualTo("2026-11-14");
        assertThat(SeedDates.date("3d", TODAY)).isEqualTo("2026-11-22");
        assertThat(SeedDates.date("2026-12-24", TODAY)).isEqualTo("2026-12-24");
        assertThat(SeedDates.date("2026-12-24T23:00:00Z", TODAY)).isEqualTo("2026-12-24");
        assertThat(SeedDates.date(null, TODAY)).isNull();
        assertThat(SeedDates.instant("-1d", NOW)).isEqualTo("2026-11-18T10:00:00.123Z");
        assertThat(SeedDates.instant("+2d", NOW)).isEqualTo("2026-11-21T10:00:00.123Z");
        assertThat(SeedDates.instant("2026-01-02", NOW)).isEqualTo("2026-01-02T00:00:00Z");
        assertThat(SeedDates.instant("2026-01-02T03:04:05Z", NOW)).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(SeedDates.instant(" ", NOW)).isNull();
    }
}
