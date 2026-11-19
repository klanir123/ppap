package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** REST DTOs of the exchange (docs/api/exchange-api.md). */
final class ExchangeDtos {

    private ExchangeDtos() {}

    /** Partner of a message. */
    record PartnerRefDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String bpnl,

            String name) {}

    /** Step of a message. */
    record ExchangeStepDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            StepName step,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            StepStatus status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant at,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long durationMs,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Delivery attempt / processing run")
            int attempt,

            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "assetId, offerId, negotiationId, agreementId, transferProcessId, httpStatus, "
                            + "cachedEdr, error …")
            Map<String, Object> details) {}

    /** Exchange record (without payload). */
    record ExchangeRecordDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID messageId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Direction direction,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String messageType,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            PartnerRefDto partner,

            UUID caseId,
            String caseNumber,
            UUID exchangeId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            MessageStatus status,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int attempts,

            Instant nextAttemptAt,
            String payloadHash,
            String correlationId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt,

            Instant completedAt,

            @Schema(description = "Rejection or error code, e.g. REJECTED_UNKNOWN_PARTNER, EDC_CATALOG")
            String errorCode,

            String lastError,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean retryable,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ExchangeStepDto> steps) {}

    /** Exchange record with the envelope (diagnosis). */
    record ExchangeMessageDetailDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ExchangeRecordDto record,

            @Schema(description = "PpapNotification envelope as sent/received (JSON), or the raw text if invalid")
            Object payload,

            @Schema(description = "Consumer BPN forwarded by the own data plane (Edc-Bpn), inbound only")
            String edcBpn) {}

    /** Page of exchange records. */
    record ExchangeMessagePage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ExchangeRecordDto> content,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int page,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int size,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long totalElements,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int totalPages) {

        static ExchangeMessagePage of(Page<ExchangeRecordDto> page) {
            return new ExchangeMessagePage(
                    page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    /** Connector state. */
    enum EdcState {
        UP,
        DOWN
    }

    /** Provisioning state. */
    record ProvisioningDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> assets,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "BPNLs in the access group")
            List<String> partners,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean ok,

            Instant lastRun,
            String error) {}

    record OutboxDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long pending,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long failed) {}

    record InboxDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long rejected) {}

    /** Overall exchange status. */
    record ExchangeStatusDto(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            EdcState edc,

            String edcError,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ProvisioningDto provisioning,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            OutboxDto outbox,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            InboxDto inbox) {}

    static ExchangeRecordDto record(ExchangeRecord r) {
        return new ExchangeRecordDto(
                r.id(),
                r.messageId(),
                r.direction(),
                r.messageType(),
                new PartnerRefDto(r.partnerBpnl(), r.partnerName()),
                r.caseId(),
                r.caseNumber(),
                r.exchangeId(),
                r.status(),
                r.attempts(),
                r.nextAttemptAt(),
                r.payloadHash(),
                r.correlationId(),
                r.createdAt(),
                r.completedAt(),
                r.errorCode(),
                r.lastError(),
                r.retryable(),
                r.steps().stream().map(ExchangeDtos::step).toList());
    }

    static ExchangeStepDto step(ExchangeStep s) {
        return new ExchangeStepDto(s.step(), s.status(), s.at(), s.durationMs(), s.attempt(), s.details());
    }
}
