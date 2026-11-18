package com.msg.ppap.masterdata.application;

import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.MasterdataChanged;
import com.msg.ppap.masterdata.domain.MasterdataAggregate;
import java.time.Clock;
import java.util.LinkedHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes {@link MasterdataChanged} events (transactional, via the Modulith event publication registry). */
@Component
public class MasterdataEvents {

    private final ApplicationEventPublisher publisher;
    private final TenantContext tenant;
    private final ActorProvider actors;
    private final Clock clock;

    public MasterdataEvents(
            ApplicationEventPublisher publisher, TenantContext tenant, ActorProvider actors, Clock clock) {
        this.publisher = publisher;
        this.tenant = tenant;
        this.actors = actors;
        this.clock = clock;
    }

    /** Publishes a change of the given aggregate. */
    public void changed(MasterdataAggregate<?> aggregate, MasterdataChanged.Action action) {
        var details = new LinkedHashMap<String, Object>();
        details.put("businessKey", aggregate.businessKey());
        if (aggregate.version() != null && action != MasterdataChanged.Action.DELETED) {
            details.put("version", aggregate.version());
        }
        details.put("active", aggregate.active());
        publisher.publishEvent(new MasterdataChanged(
                tenant.tenantId(),
                clock.instant(),
                actors.currentActor(),
                action.name(),
                aggregate.type().name(),
                aggregate.id().toString(),
                details));
    }
}
