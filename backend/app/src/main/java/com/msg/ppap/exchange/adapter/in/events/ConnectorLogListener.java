package com.msg.ppap.exchange.adapter.in.events;

import com.msg.ppap.exchange.application.LiveEventService;
import com.msg.ppap.exchange.domain.ExchangeStepRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Forwards every persisted exchange step to the live stream as {@code connector.log} (UC-10). */
@Component
class ConnectorLogListener {

    private final LiveEventService live;

    ConnectorLogListener(LiveEventService live) {
        this.live = live;
    }

    @EventListener
    void on(ExchangeStepRecorded recorded) {
        live.connectorLog(recorded);
    }
}
