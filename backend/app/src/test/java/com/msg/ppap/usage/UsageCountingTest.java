package com.msg.ppap.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.approval.application.port.in.RevisionFactsUseCase;
import com.msg.ppap.approval.application.port.in.RevisionFactsUseCase.RevisionFact;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.usage.application.UsageService;
import com.msg.ppap.usage.application.port.out.UsageStore;
import com.msg.ppap.usage.domain.UsageEvent;
import com.msg.ppap.usage.domain.UsageMetric;
import com.msg.ppap.usage.domain.UsageRules;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Usage counting rules and service (UC-12): idempotency, recomputation equality, legacy exclusion, report, CSV. */
class UsageCountingTest {

    static final Instant NOV = Instant.parse("2026-11-03T08:00:00Z");
    static final Instant OCT = Instant.parse("2026-10-31T23:59:59Z");

    /** In-memory store with the semantics of the JDBC store. */
    static final class MemoryStore implements UsageStore {
        final Set<String> keys = new HashSet<>();
        final Map<String, Map<String, Long>> counters = new TreeMap<>();

        @Override
        public boolean count(UsageEvent event, Instant recordedAt) {
            if (!keys.add(event.exchangeId() + "/" + event.revision() + "/" + event.metric())) {
                return false;
            }
            counters.computeIfAbsent(event.month(), m -> new TreeMap<>())
                    .merge(event.metric().name(), 1L, Long::sum);
            return true;
        }

        @Override
        public Map<String, Map<String, Long>> counters(String fromMonth, String toMonth) {
            Map<String, Map<String, Long>> result = new TreeMap<>();
            counters.forEach((m, v) -> {
                if (m.compareTo(fromMonth) >= 0 && m.compareTo(toMonth) <= 0) {
                    result.put(m, v);
                }
            });
            return result;
        }

        @Override
        public Map<String, Map<String, Long>> allCounters() {
            Map<String, Map<String, Long>> copy = new TreeMap<>();
            counters.forEach((m, v) -> copy.put(m, new TreeMap<>(v)));
            return copy;
        }

        @Override
        public void replaceAll(Collection<UsageEvent> events, Instant recordedAt) {
            keys.clear();
            counters.clear();
            events.forEach(e -> count(e, recordedAt));
        }
    }

    static final class Facts implements RevisionFactsUseCase {
        final List<RevisionFact> facts = new ArrayList<>();

        @Override
        public List<RevisionFact> submittedRevisions() {
            return facts;
        }

        @Override
        public Optional<RevisionFact> submittedRevision(UUID caseId, int revision) {
            return facts.stream()
                    .filter(f -> f.caseId().equals(caseId) && f.revision() == revision)
                    .findFirst();
        }

        RevisionFact add(String role, String source, int revision, Instant at) {
            var fact = new RevisionFact(UUID.randomUUID(), UUID.randomUUID(), role, source, revision, at);
            facts.add(fact);
            return fact;
        }
    }

    final MemoryStore store = new MemoryStore();
    final Facts facts = new Facts();
    final UsageService service =
            new UsageService(store, facts, Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void rulesMapRoleAndRevisionToMetricsAndExcludeLegacy() {
        assertThat(UsageRules.metric("SUPPLIER", "EXCHANGE", 1)).contains(UsageMetric.APPROVAL_CASES_SUBMITTED);
        assertThat(UsageRules.metric("SUPPLIER", "MANUAL", 2)).contains(UsageMetric.REVISIONS_SUBMITTED);
        assertThat(UsageRules.metric("CUSTOMER", "LOCAL", 1)).contains(UsageMetric.APPROVAL_CASES_RECEIVED);
        assertThat(UsageRules.metric("CUSTOMER", "LOCAL", 3)).contains(UsageMetric.REVISIONS_RECEIVED);
        assertThat(UsageRules.metric("CUSTOMER", "LEGACY_IMPORT", 1)).isEmpty();
        assertThat(UsageRules.metric("OTHER", "LOCAL", 1)).isEmpty();
        assertThat(UsageRules.metric("SUPPLIER", "LOCAL", 0)).isEmpty();
        assertThat(UsageRules.month(OCT)).isEqualTo("2026-10");
        assertThat(UsageRules.parseMonth("2026-02").getMonthValue()).isEqualTo(2);
        assertThatThrownBy(() -> UsageRules.parseMonth("2026-13")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UsageRules.parseMonth(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(UsageMetric.APPROVAL_CASES_RECEIVED.approvalCase()).isTrue();
        assertThat(UsageMetric.REVISIONS_SUBMITTED.approvalCase()).isFalse();
    }

    @Test
    void countingIsIdempotentAndEqualsTheRecomputation() {
        RevisionFact first = facts.add("SUPPLIER", "EXCHANGE", 1, NOV);
        RevisionFact second = facts.add("SUPPLIER", "EXCHANGE", 2, NOV);
        RevisionFact received = facts.add("CUSTOMER", "LOCAL", 1, OCT);
        RevisionFact legacy = facts.add("CUSTOMER", "LEGACY_IMPORT", 1, OCT);
        assertThat(service.count(first.caseId(), 1)).isTrue();
        assertThat(service.count(first.caseId(), 1)).isFalse();
        assertThat(service.count(second.caseId(), 2)).isTrue();
        assertThat(service.count(received.caseId(), 1)).isTrue();
        assertThat(service.count(legacy.caseId(), 1)).isFalse();
        assertThat(service.count(UUID.randomUUID(), 1)).isFalse();

        UsageService.Recomputation recomputation = service.recompute();
        assertThat(recomputation.consistent()).isTrue();
        assertThat(recomputation.events()).isEqualTo(3);

        // a lost event is corrected by the recomputation and reported
        facts.add("CUSTOMER", "LOCAL", 2, NOV);
        UsageService.Recomputation corrected = service.recompute();
        assertThat(corrected.consistent()).isFalse();
        assertThat(corrected.differences()).singleElement().satisfies(d -> {
            assertThat(d.month()).isEqualTo("2026-11");
            assertThat(d.metric()).isEqualTo("REVISIONS_RECEIVED");
            assertThat(d.before()).isZero();
            assertThat(d.after()).isEqualTo(1);
        });
    }

    @Test
    void reportAggregatesMonthsAndExportsCsv() {
        facts.add("SUPPLIER", "EXCHANGE", 1, NOV);
        facts.add("CUSTOMER", "LOCAL", 1, NOV);
        facts.add("CUSTOMER", "LOCAL", 2, NOV);
        facts.add("SUPPLIER", "EXCHANGE", 1, OCT);
        service.recompute();
        List<UsageService.MonthUsage> report = service.report(null, null);
        assertThat(report).hasSize(12);
        assertThat(report.getLast().month()).isEqualTo("2026-11");
        assertThat(report.getLast().approvalCases()).isEqualTo(2);
        assertThat(report.getLast().revisions()).isEqualTo(1);
        assertThat(report.get(10).approvalCases()).isEqualTo(1);
        assertThat(report.getFirst().approvalCases()).isZero();
        assertThat(service.report("2026-10", "2026-11")).hasSize(2);
        String csv = service.csv("2026-11", "2026-11");
        assertThat(csv)
                .startsWith("month;approvalCases;revisions;APPROVAL_CASES_SUBMITTED;REVISIONS_SUBMITTED;"
                        + "APPROVAL_CASES_RECEIVED;REVISIONS_RECEIVED\n")
                .contains("2026-11;2;1;1;0;1;1\n");
        assertThatThrownBy(() -> service.report("2026-12", "2026-11")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.report("2026-1", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.report("2000-01", "2026-11")).isInstanceOf(ValidationException.class);
    }
}
