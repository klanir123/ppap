package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.CaseExchangeStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link CaseExchangeStatus} for the approval module (delivery badge of cases). */
@Service
public class CaseExchangeStatusService implements CaseExchangeStatus {

    private final ExchangeContext context;

    public CaseExchangeStatusService(ExchangeContext context) {
        this.context = context;
    }

    @Override
    public Map<UUID, LastOutbound> lastOutbound(Collection<UUID> caseIds) {
        var result = new LinkedHashMap<UUID, LastOutbound>();
        context.store()
                .lastOutbound(caseIds)
                .forEach((caseId, last) -> result.put(
                        caseId,
                        new LastOutbound(last.messageType(), last.status().name(), last.at())));
        return result;
    }
}
