package com.msg.ppap.usage.application;

import com.msg.ppap.approval.application.port.in.RevisionFactsUseCase;
import com.msg.ppap.approval.application.port.in.RevisionFactsUseCase.RevisionFact;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import com.msg.ppap.usage.application.port.out.UsageStore;
import com.msg.ppap.usage.domain.UsageEvent;
import com.msg.ppap.usage.domain.UsageMetric;
import com.msg.ppap.usage.domain.UsageRules;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usage counters (UC-12): event-based ({@code SubmissionSubmitted} at the supplier, received revisions at the
 * customer), idempotent per (exchange id, revision, metric) and reproducible from the revisions ({@link #recompute}).
 * No billing logic (AK-12-3).
 */
@Service
@Transactional
public class UsageService {

    private static final Logger LOG = LoggerFactory.getLogger(UsageService.class);
    static final int DEFAULT_MONTHS = 12;

    private final UsageStore store;
    private final RevisionFactsUseCase revisions;
    private final Clock clock;

    public UsageService(UsageStore store, RevisionFactsUseCase revisions, Clock clock) {
        this.store = store;
        this.revisions = revisions;
        this.clock = clock;
    }

    /**
     * Counts a submitted (supplier) or received (customer) revision.
     *
     * @return {@code true} if counted now, {@code false} if not countable or counted before
     */
    public boolean count(UUID caseId, int revision) {
        Optional<UsageEvent> event =
                revisions.submittedRevision(caseId, revision).flatMap(UsageService::event);
        if (event.isEmpty()) {
            return false;
        }
        boolean counted = store.count(event.get(), clock.instant());
        if (counted) {
            LOG.debug("Usage {} counted for case {} revision {}", event.get().metric(), caseId, revision);
        }
        return counted;
    }

    /** Recomputes all counters from the revisions and reports the differences to the event-based counters. */
    public Recomputation recompute() {
        Map<String, Map<String, Long>> before = store.allCounters();
        List<UsageEvent> events = new ArrayList<>();
        for (RevisionFact fact : revisions.submittedRevisions()) {
            event(fact).ifPresent(events::add);
        }
        store.replaceAll(events, clock.instant());
        Map<String, Map<String, Long>> after = store.allCounters();
        List<Difference> differences = new ArrayList<>();
        TreeSet<String> months = new TreeSet<>(before.keySet());
        months.addAll(after.keySet());
        for (String month : months) {
            for (UsageMetric metric : UsageMetric.values()) {
                long b = before.getOrDefault(month, Map.of()).getOrDefault(metric.name(), 0L);
                long a = after.getOrDefault(month, Map.of()).getOrDefault(metric.name(), 0L);
                if (a != b) {
                    differences.add(new Difference(month, metric.name(), b, a));
                }
            }
        }
        if (!differences.isEmpty()) {
            LOG.warn("Usage recomputation changed {} counter(s): {}", differences.size(), differences);
        }
        return new Recomputation(clock.instant(), events.size(), differences);
    }

    /** Monthly figures between {@code from} and {@code to} (inclusive, default: the last 12 months incl. current). */
    @Transactional(readOnly = true)
    public List<MonthUsage> report(String from, String to) {
        YearMonth end = to == null || to.isBlank() ? YearMonth.now(clock.withZone(ZoneOffset.UTC)) : month("to", to);
        YearMonth start = from == null || from.isBlank() ? end.minusMonths(DEFAULT_MONTHS - 1L) : month("from", from);
        if (start.isAfter(end)) {
            throw ValidationException.of("from", ViolationCodes.INVALID_VALUE, "must not be after 'to'");
        }
        if (start.plusMonths(120).isBefore(end)) {
            throw ValidationException.of("from", ViolationCodes.INVALID_VALUE, "at most 120 months");
        }
        Map<String, Map<String, Long>> counters = store.counters(start.toString(), end.toString());
        List<MonthUsage> result = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            Map<String, Long> details = new LinkedHashMap<>();
            Map<String, Long> stored = counters.getOrDefault(m.toString(), Map.of());
            long cases = 0;
            long revisionsCount = 0;
            for (UsageMetric metric : UsageMetric.values()) {
                long n = stored.getOrDefault(metric.name(), 0L);
                details.put(metric.name(), n);
                if (metric.approvalCase()) {
                    cases += n;
                } else {
                    revisionsCount += n;
                }
            }
            result.add(new MonthUsage(m.toString(), cases, revisionsCount, details));
        }
        return result;
    }

    /** CSV export (semicolon separated, header line) of {@link #report}. */
    @Transactional(readOnly = true)
    public String csv(String from, String to) {
        var csv = new StringBuilder("month;approvalCases;revisions");
        for (UsageMetric metric : UsageMetric.values()) {
            csv.append(';').append(metric.name());
        }
        csv.append('\n');
        for (MonthUsage month : report(from, to)) {
            csv.append(month.month())
                    .append(';')
                    .append(month.approvalCases())
                    .append(';')
                    .append(month.revisions());
            for (UsageMetric metric : UsageMetric.values()) {
                csv.append(';').append(month.details().getOrDefault(metric.name(), 0L));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    static Optional<UsageEvent> event(RevisionFact fact) {
        return UsageRules.metric(fact.ownRole(), fact.source(), fact.revision())
                .map(metric -> new UsageEvent(
                        fact.exchangeId(),
                        fact.revision(),
                        metric,
                        UsageRules.month(fact.submittedAt()),
                        fact.caseId(),
                        fact.submittedAt()));
    }

    private static YearMonth month(String field, String value) {
        try {
            return UsageRules.parseMonth(value.strip());
        } catch (IllegalArgumentException ex) {
            throw ValidationException.of(field, ViolationCodes.INVALID_FORMAT, "YYYY-MM expected");
        }
    }

    /**
     * Usage of one month.
     *
     * @param approvalCases submitted + received approval cases (revision 1)
     * @param revisions submitted + received further revisions
     * @param details count per metric
     */
    public record MonthUsage(String month, long approvalCases, long revisions, Map<String, Long> details) {}

    /** Result of a recomputation. */
    public record Recomputation(Instant recomputedAt, int events, List<Difference> differences) {

        public boolean consistent() {
            return differences.isEmpty();
        }
    }

    /** A counter that differed between the event-based counting and the recomputation. */
    public record Difference(String month, String metric, long before, long after) {}
}
