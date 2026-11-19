package com.msg.ppap.exchange.adapter.in.events;

import com.msg.ppap.exchange.application.ProvisioningService;
import com.msg.ppap.masterdata.MasterdataChanged;
import com.msg.ppap.masterdata.MasterdataSeeded;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Triggers the EDC provisioning on startup and when partners or relations change (group membership {@code
 * ppap-partners}); the scheduler applies it shortly after.
 */
@Component
class MasterdataEventsListener {

    private static final Set<String> RELEVANT = Set.of("PARTNER", "RELATION");

    private final ProvisioningService provisioning;

    MasterdataEventsListener(ProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onStartup() {
        provisioning.requestReconcile();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onChange(MasterdataChanged event) {
        if (RELEVANT.contains(event.objectType())) {
            provisioning.requestReconcile();
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onSeeded(MasterdataSeeded event) {
        provisioning.requestReconcile();
    }
}
