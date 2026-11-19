package com.msg.ppap.exchange.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Transfer state of partner documents (table {@code ex_document_transfer}); scoped to the current tenant. */
public interface DocumentTransferStore {

    /**
     * Inserts a pending transfer unless one exists for the case and SHA-256.
     *
     * @return {@code true} if inserted
     */
    boolean insertIfAbsent(Transfer transfer);

    Optional<Transfer> find(UUID caseId, String sha256);

    List<Transfer> findByCase(UUID caseId);

    /** Status change after a fetch attempt. */
    void update(
            UUID id,
            String status,
            UUID documentId,
            boolean hashVerified,
            String errorCode,
            String lastError,
            Instant at);

    /** Links the pull job record. */
    void linkMessage(UUID id, UUID messageRef, Instant at);

    /**
     * A document transfer.
     *
     * @param status {@code PENDING}, {@code FETCHED}, {@code FAILED}, {@code NOT_AVAILABLE}
     * @param messageRef pull job ({@code ex_message}) of the latest attempt
     */
    record Transfer(
            UUID id,
            UUID caseId,
            UUID exchangeId,
            String partnerBpnl,
            String sha256,
            String fileName,
            String mediaType,
            long size,
            String status,
            UUID documentId,
            boolean hashVerified,
            UUID messageRef,
            String errorCode,
            String lastError,
            Instant updatedAt) {}
}
