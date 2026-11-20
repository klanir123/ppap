package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.PartnerExchangeHealth;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.PartnerFigures;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * {@link PartnerExchangeHealth} for the dashboard (docs/api/cascade-transparency-api.md §4): {@code FAILING} if a
 * message to/from the partner failed or was rejected within the last 24 hours, {@code DEGRADED} if messages are being
 * retried or pending for more than 5 minutes, otherwise {@code OK}.
 */
@Service
public class PartnerExchangeHealthService implements PartnerExchangeHealth {

    static final Duration WINDOW = Duration.ofHours(24);

    private final ExchangeContext context;

    public PartnerExchangeHealthService(ExchangeContext context) {
        this.context = context;
    }

    @Override
    public Map<String, Health> health(Collection<String> partnerBpnls) {
        Instant now = context.now();
        Map<String, Health> result = new LinkedHashMap<>();
        context.store()
                .partnerFigures(partnerBpnls, now.minus(WINDOW), now)
                .forEach((bpnl, figures) -> result.put(bpnl, new Health(status(figures), figures.lastExchangeAt())));
        return result;
    }

    static String status(PartnerFigures figures) {
        if (figures.failed() > 0 || figures.rejected() > 0) {
            return "FAILING";
        }
        return figures.retrying() > 0 ? "DEGRADED" : "OK";
    }
}
