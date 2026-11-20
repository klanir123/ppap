package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.exchange.application.ExchangeAssets;
import com.msg.ppap.exchange.application.InboxService;
import com.msg.ppap.exchange.application.InboxService.Receipt;
import com.msg.ppap.exchange.application.ServedContentAudit;
import com.msg.ppap.identity.PublicKeys;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints called only by the own EDC data plane (assets {@code ppap-notification-receive}, {@code
 * ppap-public-keys}). Not protected by Keycloak but by the shared secret the data plane adds as header (DataAddress
 * {@code authKey}/{@code secretName}); compared in constant time. Not part of the public OpenAPI.
 */
@RestController
@Hidden
public class EdcCallbackController {

    static final String EDC_BPN_HEADER = CallbackAuthentication.EDC_BPN_HEADER;
    static final String EDC_AGREEMENT_HEADER = CallbackAuthentication.EDC_AGREEMENT_HEADER;

    private final InboxService inbox;
    private final PublicKeys publicKeys;
    private final CallbackAuthentication authentication;
    private final ServedContentAudit audit;

    EdcCallbackController(
            InboxService inbox,
            PublicKeys publicKeys,
            CallbackAuthentication authentication,
            ServedContentAudit audit) {
        this.inbox = inbox;
        this.publicKeys = publicKeys;
        this.authentication = authentication;
        this.audit = audit;
    }

    @PostMapping(path = ExchangeAssets.NOTIFICATIONS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> receive(
            HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        Receipt receipt = inbox.accept(
                body == null ? new byte[0] : body,
                request.getHeader(EDC_BPN_HEADER),
                request.getHeader(EDC_AGREEMENT_HEADER));
        return switch (receipt) {
            case Receipt.Accepted a ->
                ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(Map.of("messageId", a.messageId().toString(), "status", "ACCEPTED"));
            case Receipt.Duplicate d ->
                ResponseEntity.ok(Map.of("messageId", d.messageId().toString(), "status", "DUPLICATE"));
            case Receipt.Invalid i -> {
                var problem = new LinkedHashMap<String, Object>();
                problem.put("status", "INVALID");
                problem.put("code", "PAYLOAD_SCHEMA_VIOLATION");
                problem.put(
                        "errors",
                        i.violations().stream()
                                .map(v -> Map.of("field", v.field(), "code", v.code(), "message", v.message()))
                                .toList());
                yield ResponseEntity.badRequest().body(problem);
            }
        };
    }

    @GetMapping(path = ExchangeAssets.JWKS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks(HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthorized"));
        }
        Map<String, Object> jwks = publicKeys.publicJwkSet();
        String consumer = request.getHeader(EDC_BPN_HEADER);
        if (consumer != null && !consumer.isBlank()) {
            Object keys = jwks.get("keys");
            List<String> kids = keys instanceof List<?> list
                    ? list.stream()
                            .filter(Map.class::isInstance)
                            .map(k -> String.valueOf(((Map<?, ?>) k).get("kid")))
                            .toList()
                    : List.of();
            audit.publicKeys(kids, consumer, request.getHeader(EDC_AGREEMENT_HEADER));
        }
        return ResponseEntity.ok(jwks);
    }

    private boolean authorized(HttpServletRequest request) {
        return authentication.authorized(request);
    }
}
