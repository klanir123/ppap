package com.msg.ppap.exchange.adapter.out.jdbc;

import com.msg.ppap.exchange.application.port.out.PartnerKeyStore;
import com.msg.ppap.identity.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** JDBC persistence of {@code ex_partner_key}. */
@Repository
class JdbcPartnerKeyStore implements PartnerKeyStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    JdbcPartnerKeyStore(NamedParameterJdbcTemplate jdbc, TenantContext tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Override
    public Optional<Map<String, Object>> find(String bpnl, String kid) {
        return jdbc
                .query(
                        "select jwk from ex_partner_key where tenant_id = :tenant and bpnl = :bpnl and kid = :kid",
                        params(bpnl, kid),
                        (rs, i) -> JSON.readValue(rs.getString("jwk"), MAP))
                .stream()
                .findFirst();
    }

    @Override
    public void save(String bpnl, String kid, Map<String, Object> jwk, Instant fetchedAt) {
        jdbc.update(
                """
                insert into ex_partner_key (tenant_id, bpnl, kid, jwk, fetched_at)
                values (:tenant, :bpnl, :kid, cast(:jwk as jsonb), :fetched)
                on conflict (tenant_id, bpnl, kid) do update set jwk = excluded.jwk, fetched_at = excluded.fetched_at
                """,
                params(bpnl, kid)
                        .addValue("jwk", JSON.writeValueAsString(jwk))
                        .addValue("fetched", Timestamp.from(fetchedAt)));
    }

    private MapSqlParameterSource params(String bpnl, String kid) {
        return new MapSqlParameterSource()
                .addValue("tenant", tenant.tenantId())
                .addValue("bpnl", bpnl)
                .addValue("kid", kid);
    }
}
