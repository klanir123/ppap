package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.EdcState;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ExchangeMessageDetailDto;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ExchangeMessagePage;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ExchangeRecordDto;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ExchangeStatusDto;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.InboxDto;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.OutboxDto;
import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ProvisioningDto;
import com.msg.ppap.exchange.application.ExchangeQueryService;
import com.msg.ppap.exchange.application.ExchangeStatus;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Exchange cockpit basis (CONNECTOR_ADMIN): messages, manual retry, overall status. */
@RestController
@RequestMapping("/api/v1/exchange")
@Tag(name = "exchange")
@PreAuthorize("hasRole('CONNECTOR_ADMIN')")
public class ExchangeController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ExchangeQueryService exchange;

    public ExchangeController(ExchangeQueryService exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/messages")
    @Operation(operationId = "listExchangeMessages", summary = "Search exchanged messages (without payload)")
    public ExchangeMessagePage list(
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) MessageStatus status,
            @RequestParam(required = false) String partnerBpnl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        if (page < 0) {
            throw ValidationException.of("page", ViolationCodes.INVALID_VALUE, "must be >= 0");
        }
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 200));
        return ExchangeMessagePage.of(
                exchange.search(direction, status, partnerBpnl, pageable).map(ExchangeDtos::record));
    }

    @GetMapping("/messages/{id}")
    @Operation(operationId = "getExchangeMessage", summary = "Exchanged message with envelope (diagnosis)")
    public ExchangeMessageDetailDto get(@PathVariable UUID id) {
        StoredMessage message = exchange.detail(id);
        Object payload;
        try {
            payload = JSON.readTree(message.envelope());
        } catch (RuntimeException ex) {
            payload = message.envelope();
        }
        return new ExchangeMessageDetailDto(ExchangeDtos.record(message.record()), payload, message.edcBpn());
    }

    @PostMapping("/messages/{id}/retry")
    @Operation(
            operationId = "retryExchangeMessage",
            summary = "Retry a failed delivery (outbound FAILED) or reprocess a rejected message (inbound REJECTED)")
    public ExchangeRecordDto retry(@PathVariable UUID id) {
        return ExchangeDtos.record(exchange.retry(id));
    }

    @GetMapping("/status")
    @Operation(operationId = "getExchangeStatus", summary = "EDC, provisioning, outbox and inbox status")
    public ExchangeStatusDto status() {
        ExchangeStatus s = exchange.status();
        var p = s.provisioning();
        return new ExchangeStatusDto(
                s.edc().reachable() ? EdcState.UP : EdcState.DOWN,
                s.edc().error(),
                new ProvisioningDto(p.assets(), p.partners(), p.ok(), p.lastRun(), p.error()),
                new OutboxDto(s.pending(), s.failed()),
                new InboxDto(s.rejected()));
    }
}
