package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.application.LiveEvent;
import com.msg.ppap.exchange.application.LiveEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live updates ({@code text/event-stream}, bearer token): {@code case.changed {caseId, status}}, {@code
 * exchange.changed {caseId, messageId, status}}, {@code connector.log {at, level, correlationId, messageType, step,
 * text}} (phase 6), {@code heartbeat} every 25 s. The event name is sent in the SSE
 * {@code event:} field, the JSON object in {@code data:}. Only events of the own tenant.
 */
@RestController
@Tag(name = "events")
public class LiveEventController {

    static final Duration TIMEOUT = Duration.ofHours(1);

    private final LiveEventService live;

    public LiveEventController(LiveEventService live) {
        this.live = live;
    }

    @GetMapping(path = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            operationId = "streamEvents",
            summary = "Live updates of cases and exchange (Server-Sent Events)",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "Event stream: case.changed, exchange.changed, connector.log, heartbeat",
                            content =
                                    @Content(
                                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                            schema = @Schema(type = "string"))))
    public SseEmitter stream() {
        var emitter = new SseEmitter(TIMEOUT.toMillis());
        LiveEventService.Subscription subscription = live.subscribe(event -> send(emitter, event));
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(subscription::close);
        emitter.onError(error -> subscription.close());
        send(emitter, new LiveEvent(subscription.tenantId(), LiveEvent.HEARTBEAT, java.util.Map.of("connected", true)));
        return emitter;
    }

    private static void send(SseEmitter emitter, LiveEvent event) {
        try {
            emitter.send(SseEmitter.event().name(event.type()).data(event.data(), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
            throw new UncheckedIOException(new IOException("live event subscriber gone", ex));
        }
    }
}
