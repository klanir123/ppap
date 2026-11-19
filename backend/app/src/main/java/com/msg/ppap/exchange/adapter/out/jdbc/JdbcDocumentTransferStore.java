package com.msg.ppap.exchange.adapter.out.jdbc;

import com.msg.ppap.exchange.application.port.out.DocumentTransferStore;
import com.msg.ppap.identity.TenantContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC persistence of {@code ex_document_transfer}. */
@Repository
class JdbcDocumentTransferStore implements DocumentTransferStore {

    private static final String COLUMNS = """
            id, case_id, exchange_id, partner_bpnl, sha256, file_name, media_type, size_bytes, status, document_id,
            hash_verified, message_ref, error_code, last_error, updated_at""";

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;

    JdbcDocumentTransferStore(NamedParameterJdbcTemplate jdbc, TenantContext tenant) {
        this.jdbc = jdbc;
        this.tenant = tenant;
    }

    @Override
    public boolean insertIfAbsent(Transfer t) {
        return jdbc.update(
                        """
                        insert into ex_document_transfer (id, tenant_id, case_id, exchange_id, partner_bpnl, sha256, file_name,
                            media_type, size_bytes, status, document_id, hash_verified, message_ref, last_error, created_at,
                            updated_at)
                        values (:id, :tenant, :caseId, :exchangeId, :partner, :sha, :fileName, :mediaType, :size, :status,
                            null, false, null, null, :at, :at)
                        on conflict (tenant_id, case_id, sha256) do nothing
                        """,
                        params().addValue("id", t.id())
                                .addValue("caseId", t.caseId())
                                .addValue("exchangeId", t.exchangeId())
                                .addValue("partner", t.partnerBpnl())
                                .addValue("sha", t.sha256())
                                .addValue("fileName", t.fileName())
                                .addValue("mediaType", t.mediaType())
                                .addValue("size", t.size())
                                .addValue("status", t.status())
                                .addValue("at", Timestamp.from(t.updatedAt())))
                == 1;
    }

    @Override
    public Optional<Transfer> find(UUID caseId, String sha256) {
        return jdbc
                .query(
                        "select " + COLUMNS + " from ex_document_transfer"
                                + " where tenant_id = :tenant and case_id = :caseId and sha256 = :sha",
                        params().addValue("caseId", caseId).addValue("sha", sha256),
                        JdbcDocumentTransferStore::row)
                .stream()
                .findFirst();
    }

    @Override
    public List<Transfer> findByCase(UUID caseId) {
        return jdbc.query(
                "select " + COLUMNS + " from ex_document_transfer where tenant_id = :tenant and case_id = :caseId"
                        + " order by created_at",
                params().addValue("caseId", caseId),
                JdbcDocumentTransferStore::row);
    }

    @Override
    public void update(
            UUID id,
            String status,
            UUID documentId,
            boolean hashVerified,
            String errorCode,
            String lastError,
            Instant at) {
        jdbc.update(
                """
                update ex_document_transfer
                   set status = :status, document_id = coalesce(cast(:documentId as uuid), document_id),
                       hash_verified = :verified, error_code = :errorCode, last_error = :error, updated_at = :at
                 where tenant_id = :tenant and id = :id
                """,
                params().addValue("id", id)
                        .addValue("status", status)
                        .addValue("documentId", documentId)
                        .addValue("verified", hashVerified)
                        .addValue("errorCode", errorCode)
                        .addValue(
                                "error",
                                lastError == null || lastError.length() <= 2000
                                        ? lastError
                                        : lastError.substring(0, 2000))
                        .addValue("at", Timestamp.from(at)));
    }

    @Override
    public void linkMessage(UUID id, UUID messageRef, Instant at) {
        jdbc.update(
                "update ex_document_transfer set message_ref = :ref, updated_at = :at where tenant_id = :tenant and id = :id",
                params().addValue("id", id).addValue("ref", messageRef).addValue("at", Timestamp.from(at)));
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource().addValue("tenant", tenant.tenantId());
    }

    private static Transfer row(ResultSet rs, int i) throws SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new Transfer(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getObject("exchange_id", UUID.class),
                rs.getString("partner_bpnl"),
                rs.getString("sha256"),
                rs.getString("file_name"),
                rs.getString("media_type"),
                rs.getLong("size_bytes"),
                rs.getString("status"),
                rs.getObject("document_id", UUID.class),
                rs.getBoolean("hash_verified"),
                rs.getObject("message_ref", UUID.class),
                rs.getString("error_code"),
                rs.getString("last_error"),
                updated == null ? null : updated.toInstant());
    }
}
