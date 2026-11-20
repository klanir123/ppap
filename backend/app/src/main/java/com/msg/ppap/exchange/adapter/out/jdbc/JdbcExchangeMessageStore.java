package com.msg.ppap.exchange.adapter.out.jdbc;

import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.ExchangeStepRecorded;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * JDBC persistence of {@code ex_message} / {@code ex_message_step}. Claiming uses a single {@code UPDATE … WHERE id
 * IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …} statement, so it is atomic without an enclosing transaction and
 * safe with several application instances.
 */
@Repository
class JdbcExchangeMessageStore implements ExchangeMessageStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private static final String COLUMNS = """
            id, message_id, direction, message_type, partner_bpnl, partner_name, case_id, case_number, exchange_id, status,
            attempts, next_attempt_at, payload_hash, correlation_id, created_at, completed_at, error_code, last_error,
            envelope, edc_bpn""";

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final ApplicationEventPublisher events;

    JdbcExchangeMessageStore(NamedParameterJdbcTemplate jdbc, TenantContext tenant, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.tenant = tenant;
        this.events = events;
    }

    @Override
    public void insertOutbound(NewMessage m, Instant nextAttemptAt) {
        insert(m, MessageStatus.PENDING, nextAttemptAt, null, null);
    }

    @Override
    public boolean insertInbound(NewMessage m, MessageStatus status, String errorCode, String lastError) {
        Integer exists = jdbc.queryForObject(
                """
                select count(*) from ex_message
                where tenant_id = :tenant and direction = 'INBOUND' and partner_bpnl = :partner and message_id = :mid
                """, params().addValue("partner", m.partnerBpnl()).addValue("mid", m.messageId()), Integer.class);
        if (exists != null && exists > 0) {
            return false;
        }
        try {
            insert(m, status, null, errorCode, lastError);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    private void insert(NewMessage m, MessageStatus status, Instant nextAttemptAt, String errorCode, String error) {
        jdbc.update(
                """
                insert into ex_message (id, tenant_id, message_id, direction, message_type, partner_bpnl, partner_name,
                    case_id, case_number, exchange_id, status, attempts, next_attempt_at, payload_hash, correlation_id, envelope,
                    edc_bpn, error_code, last_error, created_at, updated_at, completed_at)
                values (:id, :tenant, :mid, :direction, :type, :partner, :partnerName, :caseId, :caseNumber, :exchangeId, :status, 0,
                    :next, :hash, :correlation, :envelope, :edcBpn, :errorCode, :error, :created, :created, :completed)
                """,
                params().addValue("id", m.id())
                        .addValue("mid", m.messageId())
                        .addValue("direction", m.direction().name())
                        .addValue("type", m.messageType())
                        .addValue("partner", m.partnerBpnl())
                        .addValue("partnerName", m.partnerName())
                        .addValue("caseId", m.caseId())
                        .addValue("caseNumber", m.caseNumber())
                        .addValue("exchangeId", m.exchangeId())
                        .addValue("status", status.name())
                        .addValue("next", ts(nextAttemptAt))
                        .addValue("hash", m.payloadHash())
                        .addValue("correlation", m.correlationId())
                        .addValue("envelope", m.envelope())
                        .addValue("edcBpn", m.edcBpn())
                        .addValue("errorCode", errorCode)
                        .addValue("error", error)
                        .addValue("created", ts(m.createdAt()))
                        .addValue("completed", status.terminal() ? ts(m.createdAt()) : null));
    }

    @Override
    public List<StoredMessage> claimOutbound(int limit, Instant now, Instant leaseUntil) {
        return jdbc.query(
                """
                update ex_message m
                   set status = 'IN_PROGRESS', locked_until = :lease, attempts = m.attempts + 1, updated_at = :now
                 where m.id in (
                     select o.id from ex_message o
                      where o.tenant_id = :tenant and o.direction = 'OUTBOUND'
                        and ((o.status = 'PENDING' and o.next_attempt_at <= :now)
                             or (o.status = 'IN_PROGRESS' and o.locked_until < :now))
                        and not exists (
                            select 1 from ex_message p
                             where p.tenant_id = o.tenant_id and p.direction = 'OUTBOUND'
                               and p.exchange_id = o.exchange_id and p.seq < o.seq
                               and p.status in ('PENDING', 'IN_PROGRESS'))
                      order by o.seq
                      limit :limit
                      for update skip locked)
                returning\s""" + COLUMNS,
                params().addValue("now", ts(now))
                        .addValue("lease", ts(leaseUntil))
                        .addValue("limit", limit),
                this::stored);
    }

    @Override
    public List<StoredMessage> claimInbound(UUID id, int limit, Instant now, Instant leaseUntil) {
        return jdbc.query(
                """
                update ex_message m
                   set locked_until = :lease, attempts = m.attempts + 1, updated_at = :now
                 where m.id in (
                     select i.id from ex_message i
                      where i.tenant_id = :tenant and i.direction = 'INBOUND' and i.status = 'RECEIVED'
                        and (cast(:id as uuid) is null or i.id = cast(:id as uuid))
                        and (i.locked_until is null or i.locked_until < :now)
                      order by i.seq
                      limit :limit
                      for update skip locked)
                returning\s""" + COLUMNS,
                params().addValue("id", id)
                        .addValue("now", ts(now))
                        .addValue("lease", ts(leaseUntil))
                        .addValue("limit", limit),
                this::stored);
    }

    @Override
    public Optional<ExchangeRecord> findInbound(String partnerBpnl, UUID messageId) {
        List<UUID> ids = jdbc.queryForList(
                """
                select id from ex_message
                 where tenant_id = :tenant and direction = 'INBOUND' and partner_bpnl = :partner and message_id = :mid
                """, params().addValue("partner", partnerBpnl).addValue("mid", messageId), UUID.class);
        return ids.isEmpty() ? Optional.empty() : findRecord(ids.getFirst());
    }

    @Override
    public List<StoredMessage> claimPulls(int limit, Instant now, Instant leaseUntil) {
        return jdbc.query(
                """
                update ex_message m
                   set status = 'IN_PROGRESS', locked_until = :lease, attempts = m.attempts + 1, updated_at = :now
                 where m.id in (
                     select p.id from ex_message p
                      where p.tenant_id = :tenant and p.direction = 'INBOUND'
                        and p.message_type in ('PULL_SUBMISSION', 'PULL_DOCUMENT')
                        and ((p.status = 'PENDING' and coalesce(p.next_attempt_at, p.created_at) <= :now)
                             or (p.status = 'IN_PROGRESS' and p.locked_until < :now))
                      order by p.seq
                      limit :limit
                      for update skip locked)
                returning\s""" + COLUMNS,
                params().addValue("now", ts(now))
                        .addValue("lease", ts(leaseUntil))
                        .addValue("limit", limit),
                this::stored);
    }

    @Override
    public void updateStatus(
            UUID id,
            MessageStatus status,
            Instant nextAttemptAt,
            String errorCode,
            String lastError,
            Instant completedAt,
            UUID caseId,
            String caseNumber) {
        jdbc.update(
                """
                update ex_message
                   set status = :status, next_attempt_at = :next, error_code = :errorCode, last_error = :error,
                       completed_at = :completed, case_id = coalesce(cast(:caseId as uuid), case_id),
                       case_number = coalesce(cast(:caseNumber as varchar), case_number),
                       locked_until = null, updated_at = now()
                 where tenant_id = :tenant and id = :id
                """,
                params().addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("next", ts(nextAttemptAt))
                        .addValue("errorCode", errorCode)
                        .addValue("error", lastError == null ? null : abbreviate(lastError))
                        .addValue("completed", ts(completedAt))
                        .addValue("caseId", caseId)
                        .addValue("caseNumber", caseNumber));
    }

    @Override
    public boolean resetForRetry(UUID id, Instant now) {
        return jdbc.update("""
                        update ex_message
                           set status = case when direction = 'OUTBOUND' or message_type in ('PULL_SUBMISSION', 'PULL_DOCUMENT')
                                             then 'PENDING' else 'RECEIVED' end,
                               attempts = case when direction = 'OUTBOUND' or message_type in ('PULL_SUBMISSION', 'PULL_DOCUMENT')
                                               then 0 else attempts end,
                               next_attempt_at = :now, locked_until = null, completed_at = null, updated_at = :now
                         where tenant_id = :tenant and id = :id
                           and ((direction = 'OUTBOUND' and status = 'FAILED')
                                or (direction = 'INBOUND' and status = 'REJECTED' and message_type <> 'UNKNOWN')
                                or (direction = 'INBOUND' and status = 'FAILED'
                                    and message_type in ('PULL_SUBMISSION', 'PULL_DOCUMENT')))
                        """, params().addValue("id", id).addValue("now", ts(now))) == 1;
    }

    @Override
    public void addStep(UUID id, ExchangeStep step) {
        insertStep(id, step);
        // phase 6: live log of the connector cockpit (connector.log), derived from the persisted steps
        jdbc.query(
                        """
                        select message_id, message_type, correlation_id, case_id, direction, partner_bpnl
                          from ex_message where tenant_id = :tenant and id = :id
                        """,
                        params().addValue("id", id),
                        (rs, i) -> new ExchangeStepRecorded(
                                id,
                                rs.getObject("message_id", UUID.class),
                                rs.getString("message_type"),
                                rs.getString("correlation_id"),
                                rs.getObject("case_id", UUID.class),
                                Direction.valueOf(rs.getString("direction")),
                                rs.getString("partner_bpnl"),
                                step))
                .forEach(events::publishEvent);
    }

    @Override
    public Map<String, PartnerFigures> partnerFigures(
            java.util.Collection<String> partnerBpnls, Instant since, Instant now) {
        Map<String, PartnerFigures> figures = new LinkedHashMap<>();
        if (partnerBpnls.isEmpty()) {
            return figures;
        }
        jdbc.query(
                """
                select partner_bpnl,
                       count(*) filter (where status = 'FAILED' and updated_at >= :since) as failed,
                       count(*) filter (where direction = 'INBOUND' and status = 'REJECTED' and updated_at >= :since)
                           as rejected,
                       count(*) filter (where status in ('PENDING', 'IN_PROGRESS')
                                          and (attempts > 1 or created_at < :stale)) as retrying,
                       max(coalesce(completed_at, updated_at)) as last_at
                  from ex_message
                 where tenant_id = :tenant and partner_bpnl in (:partners)
                 group by partner_bpnl
                """,
                params().addValue("partners", partnerBpnls)
                        .addValue("since", ts(since))
                        .addValue("stale", ts(now.minus(java.time.Duration.ofMinutes(5)))),
                rs -> {
                    figures.put(
                            rs.getString("partner_bpnl"),
                            new PartnerFigures(
                                    rs.getLong("failed"),
                                    rs.getLong("rejected"),
                                    rs.getLong("retrying"),
                                    instant(rs.getTimestamp("last_at"))));
                });
        return figures;
    }

    private void insertStep(UUID id, ExchangeStep step) {
        jdbc.update(
                """
                insert into ex_message_step (id, tenant_id, message_ref, attempt, step, status, at, duration_ms, details)
                values (:id, :tenant, :ref, :attempt, :step, :status, :at, :duration, cast(:details as jsonb))
                """,
                params().addValue("id", Ids.newId())
                        .addValue("ref", id)
                        .addValue("attempt", step.attempt())
                        .addValue("step", step.step().name())
                        .addValue("status", step.status().name())
                        .addValue("at", ts(step.at()))
                        .addValue("duration", step.durationMs())
                        .addValue("details", JSON.writeValueAsString(step.details())));
    }

    @Override
    public Optional<StoredMessage> find(UUID id) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from ex_message where tenant_id = :tenant and id = :id",
                        params().addValue("id", id),
                        this::stored)
                .stream()
                .findFirst()
                .map(s -> new StoredMessage(withSteps(List.of(s.record())).getFirst(), s.envelope(), s.edcBpn()));
    }

    @Override
    public Optional<ExchangeRecord> findRecord(UUID id) {
        return find(id).map(StoredMessage::record);
    }

    @Override
    public List<ExchangeRecord> findByCase(UUID caseId) {
        return withSteps(jdbc
                .query(
                        "select " + COLUMNS
                                + " from ex_message where tenant_id = :tenant and case_id = :caseId order by seq desc",
                        params().addValue("caseId", caseId),
                        this::stored)
                .stream()
                .map(StoredMessage::record)
                .toList());
    }

    @Override
    public Page<ExchangeRecord> search(
            Direction direction,
            MessageStatus status,
            String partnerBpnl,
            java.util.Collection<String> messageTypes,
            Pageable pageable) {
        List<String> types = messageTypes == null ? List.of() : List.copyOf(messageTypes);
        var params = params().addValue("direction", direction == null ? null : direction.name())
                .addValue("typesEmpty", types.isEmpty())
                .addValue("types", types.isEmpty() ? List.of("-") : types)
                .addValue("status", status == null ? null : status.name())
                .addValue("partner", partnerBpnl == null || partnerBpnl.isBlank() ? null : partnerBpnl.strip());
        String where = """
                where tenant_id = :tenant
                  and (cast(:direction as varchar) is null or direction = cast(:direction as varchar))
                  and (cast(:status as varchar) is null or status = cast(:status as varchar))
                  and (cast(:partner as varchar) is null or partner_bpnl = cast(:partner as varchar))
                  and (:typesEmpty or message_type in (:types))
                """;
        Long total = jdbc.queryForObject("select count(*) from ex_message " + where, params, Long.class);
        List<ExchangeRecord> content = List.of();
        if (total != null && total > 0) {
            params.addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
            content = withSteps(jdbc
                    .query(
                            "select " + COLUMNS + " from ex_message " + where
                                    + " order by seq desc limit :limit offset :offset",
                            params,
                            this::stored)
                    .stream()
                    .map(StoredMessage::record)
                    .toList());
        }
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Map<UUID, LastOutbound> lastOutbound(java.util.Collection<UUID> caseIds) {
        if (caseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, LastOutbound> result = new LinkedHashMap<>();
        jdbc.query("""
                select distinct on (case_id) case_id, message_type, status, updated_at from ex_message
                where tenant_id = :tenant and direction = 'OUTBOUND' and case_id in (:ids)
                order by case_id, seq desc
                """, params().addValue("ids", List.copyOf(caseIds)), rs -> {
            result.put(
                    rs.getObject("case_id", UUID.class),
                    new LastOutbound(
                            rs.getString("message_type"),
                            MessageStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("updated_at").toInstant()));
        });
        return result;
    }

    @Override
    public Map<String, Long> countByDirectionAndStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query(
                "select direction, status, count(*) as n from ex_message where tenant_id = :tenant group by direction, status",
                params(),
                rs -> {
                    counts.put(rs.getString("direction") + "/" + rs.getString("status"), rs.getLong("n"));
                });
        return counts;
    }

    // ---------------------------------------------------------------------------------------------------------

    private List<ExchangeRecord> withSteps(List<ExchangeRecord> records) {
        if (records.isEmpty()) {
            return records;
        }
        List<UUID> ids = records.stream().map(ExchangeRecord::id).toList();
        Map<UUID, List<ExchangeStep>> steps = jdbc
                .query(
                        """
                        select message_ref, attempt, step, status, at, duration_ms, details from ex_message_step
                        where tenant_id = :tenant and message_ref in (:ids) order by seq
                        """,
                        params().addValue("ids", ids),
                        (rs, i) -> Map.entry(
                                rs.getObject("message_ref", UUID.class),
                                new ExchangeStep(
                                        rs.getInt("attempt"),
                                        StepName.valueOf(rs.getString("step")),
                                        StepStatus.valueOf(rs.getString("status")),
                                        rs.getTimestamp("at").toInstant(),
                                        rs.getLong("duration_ms"),
                                        JSON.readValue(rs.getString("details"), MAP))))
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));
        return records.stream()
                .map(r -> r.withSteps(steps.getOrDefault(r.id(), List.of())))
                .toList();
    }

    private StoredMessage stored(ResultSet rs, int row) throws SQLException {
        var record = new ExchangeRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("message_id", UUID.class),
                Direction.valueOf(rs.getString("direction")),
                rs.getString("message_type"),
                rs.getString("partner_bpnl"),
                rs.getString("partner_name"),
                rs.getObject("case_id", UUID.class),
                rs.getString("case_number"),
                rs.getObject("exchange_id", UUID.class),
                MessageStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                instant(rs.getTimestamp("next_attempt_at")),
                rs.getString("payload_hash"),
                rs.getString("correlation_id"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("completed_at")),
                rs.getString("error_code"),
                rs.getString("last_error"),
                List.of());
        return new StoredMessage(record, rs.getString("envelope"), rs.getString("edc_bpn"));
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource().addValue("tenant", tenant.tenantId());
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String abbreviate(String text) {
        return text.length() <= 4000 ? text : text.substring(0, 3990) + "…";
    }
}
