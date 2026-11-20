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

    /**
     * Claims due pull jobs (inbound {@code PULL_SUBMISSION} / {@code PULL_DOCUMENT}, {@code SKIP LOCKED}): pending and
     * due, or in progress with expired lease. Claimed jobs are {@code IN_PROGRESS} with a lease and an incremented
     * attempt counter.
     */
    List<StoredMessage> claimPulls(int limit, Instant now, Instant leaseUntil);

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

    /**
     * Manual retry: outbound {@code FAILED → PENDING} (attempts reset), inbound {@code REJECTED → RECEIVED}, pull jobs
     * {@code FAILED | REJECTED → PENDING} (attempts reset).
     */
    boolean resetForRetry(UUID id, Instant now);

    void addStep(UUID id, ExchangeStep step);

    Optional<StoredMessage> find(UUID id);

    /** Inbound record (notification or pull job) of the partner with the given message id. */
    Optional<ExchangeRecord> findInbound(String partnerBpnl, UUID messageId);

    Optional<ExchangeRecord> findRecord(UUID id);

    List<ExchangeRecord> findByCase(UUID caseId);

    default Page<ExchangeRecord> search(
            Direction direction, MessageStatus status, String partnerBpnl, Pageable pageable) {
        return search(direction, status, partnerBpnl, List.of(), pageable);
    }

    /**
     * Search with message/record types (e.g. {@code PPAP_SUBMISSION}, {@code PULL_DOCUMENT}); empty = all.
     */
    Page<ExchangeRecord> search(
            Direction direction,
            MessageStatus status,
            String partnerBpnl,
            java.util.Collection<String> messageTypes,
            Pageable pageable);

    /** Latest outbound message per case (type, status, time of last change). */
    Map<UUID, LastOutbound> lastOutbound(java.util.Collection<UUID> caseIds);

    /** Latest outbound message of a case. */
    record LastOutbound(String messageType, MessageStatus status, Instant at) {}

    /** Number of messages per direction and status. */
    Map<String, Long> countByDirectionAndStatus();

    /**
     * Exchange figures per partner since the given time (dashboard health, phase 6).
     *
     * @return figures per partner BPNL; partners without messages are missing
     */
    Map<String, PartnerFigures> partnerFigures(java.util.Collection<String> partnerBpnls, Instant since, Instant now);

    /**
     * Exchange figures of a partner.
     *
     * @param failed failed outbound messages / pull jobs since the reference time
     * @param rejected rejected inbound messages since the reference time
     * @param retrying open messages with more than one attempt or pending longer than 5 minutes
     * @param lastExchangeAt last change of any message with the partner (all time)
     */
    record PartnerFigures(long failed, long rejected, long retrying, Instant lastExchangeAt) {}

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
