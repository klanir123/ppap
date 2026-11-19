package com.msg.ppap.approval.adapter.out.jdbc;

import com.msg.ppap.approval.application.port.out.CaseNumberGenerator;
import com.msg.ppap.identity.TenantContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Case numbers from the counter table {@code ap_case_number_counter} (one row per tenant and year). The atomic
 * {@code INSERT … ON CONFLICT DO UPDATE … RETURNING} locks the row until the end of the transaction: concurrent
 * creations are serialized per tenant and year and a rolled back creation does not leave a gap. Numbers of an existing
 * case are skipped defensively (e.g. data imported before the counter existed).
 */
@Component
class JdbcCaseNumberGenerator implements CaseNumberGenerator {

    static final String FORMAT = "PPF-%d-%05d";
    static final Pattern NUMBER = Pattern.compile("^PPF-(\\d{4})-(\\d{1,9})$");

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    JdbcCaseNumberGenerator(NamedParameterJdbcTemplate jdbc, TenantContext tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Override
    public String next(int year) {
        var params = new MapSqlParameterSource()
                .addValue("tenant", tenant.tenantId())
                .addValue("year", year);
        while (true) {
            Long value = jdbc.queryForObject("""
                    insert into ap_case_number_counter (tenant_id, year, last_value) values (:tenant, :year, 1)
                    on conflict (tenant_id, year)
                    do update set last_value = ap_case_number_counter.last_value + 1
                    returning last_value
                    """, params, Long.class);
            String number = FORMAT.formatted(year, value);
            Boolean used = jdbc.queryForObject(
                    "select exists(select 1 from ap_case where tenant_id = :tenant and case_number = :number)",
                    new MapSqlParameterSource()
                            .addValue("tenant", tenant.tenantId())
                            .addValue("number", number),
                    Boolean.class);
            if (!Boolean.TRUE.equals(used)) {
                return number;
            }
        }
    }

    @Override
    public void reserve(String caseNumber) {
        Matcher m = NUMBER.matcher(caseNumber);
        if (!m.matches()) {
            return;
        }
        jdbc.update(
                """
                insert into ap_case_number_counter (tenant_id, year, last_value) values (:tenant, :year, :value)
                on conflict (tenant_id, year)
                do update set last_value = greatest(ap_case_number_counter.last_value, excluded.last_value)
                """,
                new MapSqlParameterSource()
                        .addValue("tenant", tenant.tenantId())
                        .addValue("year", Integer.parseInt(m.group(1)))
                        .addValue("value", Long.parseLong(m.group(2))));
    }
}
