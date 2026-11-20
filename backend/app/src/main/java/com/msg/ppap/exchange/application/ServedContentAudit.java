package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.PartnerContentServed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records content served to partners via the own data plane as audit events {@code SERVED_TO_PARTNER} (phase 6,
 * backlog phase 5): submodels at the case, documents at the document, public keys at the tenant. The event is
 * published in a transaction so that the audit module records it after commit.
 */
@Service
public class ServedContentAudit {

    private final ApplicationEventPublisher events;
    private final ExchangeContext context;

    public ServedContentAudit(ApplicationEventPublisher events, ExchangeContext context) {
        this.events = events;
        this.context = context;
    }

    /** A submitted revision (submodel or its signature) was served. */
    @Transactional
    public void submission(
            UUID caseId, UUID exchangeId, int revision, String payloadHash, String consumer, String agreementId) {
        Map<String, Object> details = base(consumer, agreementId, "SUBMODEL");
        details.put("exchangeId", exchangeId.toString());
        details.put("revision", revision);
        details.put("payloadHash", payloadHash);
        publish("APPROVAL_CASE", caseId.toString(), consumer, details);
    }

    /** A document was served. */
    @Transactional
    public void document(UUID documentId, String sha256, long size, String consumer, String agreementId) {
        Map<String, Object> details = base(consumer, agreementId, "DOCUMENT");
        details.put("sha256", sha256);
        details.put("size", size);
        publish("DOCUMENT", documentId.toString(), consumer, details);
    }

    /** The public signature keys were served. */
    @Transactional
    public void publicKeys(List<String> keyIds, String consumer, String agreementId) {
        Map<String, Object> details = base(consumer, agreementId, "PUBLIC_KEYS");
        details.put("kids", List.copyOf(keyIds));
        publish(PartnerContentServed.PUBLIC_KEYS, context.tenantBpnl(), consumer, details);
    }

    private void publish(String objectType, String objectId, String consumer, Map<String, Object> details) {
        events.publishEvent(new PartnerContentServed(
                context.tenantBpnl(),
                context.now(),
                "partner:" + (consumer == null || consumer.isBlank() ? "unknown" : consumer),
                objectType,
                objectId,
                details));
    }

    private static Map<String, Object> base(String consumer, String agreementId, String kind) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("kind", kind);
        if (consumer != null && !consumer.isBlank()) {
            details.put("partnerBpnl", consumer);
        }
        if (agreementId != null && !agreementId.isBlank()) {
            details.put("agreementId", agreementId);
        }
        return details;
    }
}
