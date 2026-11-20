package com.msg.ppap.usage.adapter.out.jdbc;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.usage.application.port.out.UsageStore;
import com.msg.ppap.usage.domain.UsageEvent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence of {@code usage_event} / {@code usage_counter}. */
@Repository
class JdbcUsageStore implements UsageStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    JdbcUsageStore(NamedParameterJdbcTemplate jdbc, TenantContext tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Override
    public boolean count(UsageEvent event, Instant recordedAt) {
        int inserted = jdbc.update("""
                insert into usage_event (tenant_id, exchange_id, revision, metric, month, case_id, occurred_at, recorded_at)
                values (:tenant, :exchange, :revision, :metric, :month, :case, :occurred, :recorded)
                on conflict do nothing
                """, params(event, recordedAt));
        if (inserted == 0) {
            return false;
        }
        increment(event.month(), event.metric().name(), 1);
        return true;
    }

    @Override
    public Map<String, Map<String, Long>> counters(String fromMonth, String toMonth) {
        return query(
                "select month, metric, count from usage_counter where tenant_id = :tenant"
                        + " and month between :from and :to",
                new MapSqlParameterSource()
                        .addValue("tenant", tenant.tenantId())
                        .addValue("from", fromMonth)
                        .addValue("to", toMonth));
    }

    @Override
    public Map<String, Map<String, Long>> allCounters() {
        return query(
                "select month, metric, count from usage_counter where tenant_id = :tenant",
                new MapSqlParameterSource().addValue("tenant", tenant.tenantId()));
    }

    @Override
    public void replaceAll(Collection<UsageEvent> events, Instant recordedAt) {
        var tenantParam = new MapSqlParameterSource().addValue("tenant", tenant.tenantId());
        jdbc.update("delete from usage_counter where tenant_id = :tenant", tenantParam);
        jdbc.update("delete from usage_event where tenant_id = :tenant", tenantParam);
        for (UsageEvent event : events) {
            count(event, recordedAt);
        }
    }

    private void increment(String month, String metric, long delta) {
        jdbc.update(
                """
                insert into usage_counter (tenant_id, month, metric, count) values (:tenant, :month, :metric, :delta)
                on conflict (tenant_id, month, metric) do update set count = usage_counter.count + excluded.count
                """,
                new MapSqlParameterSource()
                        .addValue("tenant", tenant.tenantId())
                        .addValue("month", month)
                        .addValue("metric", metric)
                        .addValue("delta", delta));
    }

    private Map<String, Map<String, Long>> query(String sql, MapSqlParameterSource params) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        jdbc.query(sql, params, rs -> {
            result.computeIfAbsent(rs.getString("month").strip(), m -> new TreeMap<>())
                    .put(rs.getString("metric"), rs.getLong("count"));
        });
        return result;
    }

    private MapSqlParameterSource params(UsageEvent event, Instant recordedAt) {
        return new MapSqlParameterSource()
                .addValue("tenant", tenant.tenantId())
                .addValue("exchange", event.exchangeId())
                .addValue("revision", event.revision())
                .addValue("metric", event.metric().name())
                .addValue("month", event.month())
                .addValue("case", event.caseId())
                .addValue("occurred", Timestamp.from(event.occurredAt()))
                .addValue("recorded", Timestamp.from(recordedAt));
    }
}
