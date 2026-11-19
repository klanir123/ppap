package com.msg.ppap.twin.adapter.out.jdbc;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.twin.application.port.out.TwinStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence of {@code tw_twin}. */
@Repository
class JdbcTwinStore implements TwinStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    JdbcTwinStore(NamedParameterJdbcTemplate jdbc, TenantContext tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Override
    public void save(UUID exchangeId, UUID caseId, String shellId, String shell, Instant at) {
        jdbc.update(
                """
                insert into tw_twin (tenant_id, exchange_id, case_id, shell_id, shell, registered_at, updated_at)
                values (:tenant, :exchangeId, :caseId, :shellId, :shell, :at, :at)
                on conflict (tenant_id, exchange_id) do update
                   set shell = excluded.shell, case_id = excluded.case_id, updated_at = excluded.updated_at
                """,
                new MapSqlParameterSource()
                        .addValue("tenant", tenant.tenantId())
                        .addValue("exchangeId", exchangeId)
                        .addValue("caseId", caseId)
                        .addValue("shellId", shellId)
                        .addValue("shell", shell)
                        .addValue("at", Timestamp.from(at)));
    }

    @Override
    public Optional<StoredTwin> find(UUID exchangeId) {
        return jdbc
                .query(
                        "select exchange_id, case_id, shell_id, shell, registered_at, updated_at from tw_twin"
                                + " where tenant_id = :tenant and exchange_id = :exchangeId",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenant.tenantId())
                                .addValue("exchangeId", exchangeId),
                        (rs, i) -> new StoredTwin(
                                rs.getObject("exchange_id", UUID.class),
                                rs.getObject("case_id", UUID.class),
                                rs.getString("shell_id"),
                                rs.getString("shell"),
                                rs.getTimestamp("registered_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant()))
                .stream()
                .findFirst();
    }
}
