package com.msg.ppap.usage.application.port.out;

import com.msg.ppap.usage.domain.UsageEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Persistence of usage events and counters ({@code usage_event}, {@code usage_counter}); scoped to the tenant. */
public interface UsageStore {

    /**
     * Records the event and increments its counter (idempotent).
     *
     * @return {@code false} if the event had been counted before
     */
    boolean count(UsageEvent event, Instant recordedAt);

    /** Counters {@code month → metric → count} between the given months (inclusive). */
    Map<String, Map<String, Long>> counters(String fromMonth, String toMonth);

    /** All counters of the tenant {@code month → metric → count}. */
    Map<String, Map<String, Long>> allCounters();

    /** Replaces all events and counters of the tenant (recomputation). */
    void replaceAll(Collection<UsageEvent> events, Instant recordedAt);
}
