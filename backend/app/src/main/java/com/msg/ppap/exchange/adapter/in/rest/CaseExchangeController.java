package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.adapter.in.rest.ExchangeDtos.ExchangeRecordDto;
import com.msg.ppap.exchange.application.ExchangeQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Exchange records of a case (any authenticated role of the tenant). */
@RestController
@Tag(name = "approval-cases")
public class CaseExchangeController {

    private final ExchangeQueryService exchange;

    public CaseExchangeController(ExchangeQueryService exchange) {
        this.exchange = exchange;
    }

    @GetMapping("/api/v1/approval-cases/{id}/exchange")
    @Operation(
            operationId = "getApprovalCaseExchange",
            summary = "Messages exchanged with the partner for a case, with steps (newest first)")
    public List<ExchangeRecordDto> exchange(@PathVariable UUID id) {
        return exchange.byCase(id).stream().map(ExchangeDtos::record).toList();
    }
}
