package com.msg.ppap.exchange.application.port.out;

import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.MessageStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Persistence of exchanged messages: transactional outbox (outbound), inbox (inbound) and exchange records in one
 * table; scoped to the current tenant.
 */
public interface ExchangeMessageStore {

    /** Inserts a pending outbound message (within the caller's transaction). */
    void insertOutbound(NewMessage message, Instant nextAttemptAt);

    /**
     * Inserts a received message.
     *
     * @return {@code false} if a message with the same sender and message id exists (duplicate)
     */
    boolean insertInbound(NewMessage message, MessageStatus status, String errorCode, String lastError);

    /**
     * Claims due outbound messages ({@code FOR UPDATE SKIP LOCKED}): pending and due, or in progress with expired
     * lease; per exchange id only the oldest open message (in-order delivery). Claimed messages are {@code
     * IN_PROGRESS} with a lease and an incremented attempt counter.
     */
    List<StoredMessage> claimOutbound(int limit, Instant now, Instant leaseUntil);

    /**
     * Claims received inbound messages for processing (explicit id or all due ones).
     *
     * @param id a single message, or {@code null} for all received messages without valid lease
     */
    List<StoredMessage> claimInbound(UUID id, int limit, Instant now, Instant leaseUntil);

    /** Final or intermediate status change. */
    void updateStatus(
            UUID id,
            MessageStatus status,
            Instant nextAttemptAt,
            String errorCode,
            String lastError,
            Instant completedAt,
            UUID caseId,
            String caseNumber);

    /** Manual retry: outbound {@code FAILED → PENDING} (attempts reset), inbound {@code REJECTED → RECEIVED}. */
    boolean resetForRetry(UUID id, Instant now);

    void addStep(UUID id, ExchangeStep step);

    Optional<StoredMessage> find(UUID id);

    Optional<ExchangeRecord> findRecord(UUID id);

    List<ExchangeRecord> findByCase(UUID caseId);

    Page<ExchangeRecord> search(Direction direction, MessageStatus status, String partnerBpnl, Pageable pageable);

    /** Latest outbound message per case (type, status, time of last change). */
    Map<UUID, LastOutbound> lastOutbound(java.util.Collection<UUID> caseIds);

    /** Latest outbound message of a case. */
    record LastOutbound(String messageType, MessageStatus status, Instant at) {}

    /** Number of messages per direction and status. */
    Map<String, Long> countByDirectionAndStatus();

    /**
     * A new message.
     *
     * @param envelope serialized {@code PpapNotification} (outbound: as sent; inbound: as received)
     * @param edcBpn consumer BPN forwarded by the own data plane ({@code Edc-Bpn}), inbound only
     */
    record NewMessage(
            UUID id,
            UUID messageId,
            Direction direction,
            String messageType,
            String partnerBpnl,
            String partnerName,
            UUID caseId,
            String caseNumber,
            UUID exchangeId,
            String payloadHash,
            String correlationId,
            String envelope,
            String edcBpn,
            Instant createdAt) {}

    /** A message with its envelope (for processing and diagnosis). */
    record StoredMessage(ExchangeRecord record, String envelope, String edcBpn) {}
}
