package com.msg.ppap.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.exchange.application.LiveEvent;
import com.msg.ppap.exchange.application.LiveEventService;
import com.msg.ppap.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SSE events are published after commit: a subscriber that refetches on the event (via another connection, as the UI
 * does) sees the committed state; events of rolled-back transactions are dropped; without transaction they go out
 * immediately; events published from {@code afterCommit} callbacks are still delivered.
 */
@TestPropertySource(
        properties = {"ppap.edc.callback-secret=test-callback-secret", "ppap.exchange.worker-interval=200ms"})
class LiveEventsAfterCommitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    LiveEventService live;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    /** Status of the test row as seen by a refetch on another connection (READ COMMITTED). */
    String refetch(String month) {
        List<Long> counts = jdbc.queryForList(
                "select count from usage_counter where tenant_id = 'BPNLLIVETEST0001' and month = ? and metric = 'T'",
                Long.class,
                month);
        return counts.isEmpty() ? "MISSING" : "COMMITTED";
    }

    @Test
    void subscribersRefetchingOnTheEventSeeTheCommittedState() throws Exception {
        String caseId = UUID.randomUUID().toString();
        String month = "2099-0" + (1 + (int) (Math.random() * 8));
        List<String> seen = new CopyOnWriteArrayList<>();
        try (var subscription = live.subscribe(event -> {
            if (LiveEvent.CASE_CHANGED.equals(event.type())
                    && caseId.equals(event.data().get("caseId"))) {
                // the "UI" refetches in its own transaction/connection
                seen.add(event.data().get("status") + ":" + tx.execute(s -> refetch(month)));
            }
        })) {
            tx.executeWithoutResult(status -> {
                jdbc.update(
                        "insert into usage_counter (tenant_id, month, metric, count) values ('BPNLLIVETEST0001', ?, 'T', 1)",
                        month);
                live.caseChanged(caseId, "DELIVERED");
                assertThat(seen).as("not delivered before commit").isEmpty();
            });
            assertThat(seen).containsExactly("DELIVERED:COMMITTED");

            // rolled back → dropped
            tx.executeWithoutResult(status -> {
                live.caseChanged(caseId, "ROLLED_BACK");
                status.setRollbackOnly();
            });
            assertThat(seen).hasSize(1);

            // published from an afterCommit callback → still delivered
            tx.executeWithoutResult(status ->
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            live.caseChanged(caseId, "FROM_AFTER_COMMIT");
                        }
                    }));
            assertThat(seen).last().asString().startsWith("FROM_AFTER_COMMIT");

            // no transaction → immediately
            live.caseChanged(caseId, "NO_TX");
            assertThat(seen).last().asString().startsWith("NO_TX");
        }
    }
}
