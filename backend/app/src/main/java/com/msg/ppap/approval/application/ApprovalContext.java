package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.out.ActorContext;
import com.msg.ppap.approval.application.port.out.ApprovalCaseRepository;
import com.msg.ppap.approval.config.DecisionProperties;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseChange;
import com.msg.ppap.approval.domain.CasePolicy;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared infrastructure of the approval use cases: actor, policy, clock, loading and saving with events. */
@Component
public class ApprovalContext {

    private final ApprovalCaseRepository repository;
    private final ApprovalEventPublisher events;
    private final ActorContext actors;
    private final TenantContext tenant;
    private final DecisionProperties decision;
    private final Clock clock;

    public ApprovalContext(
            ApprovalCaseRepository repository,
            ApprovalEventPublisher events,
            ActorContext actors,
            TenantContext tenant,
            DecisionProperties decision,
            Clock clock) {
        this.repository = repository;
        this.events = events;
        this.actors = actors;
        this.tenant = tenant;
        this.decision = decision;
        this.clock = clock;
    }

    public Actor actor() {
        return actors.currentActor();
    }

    public CasePolicy policy() {
        return new CasePolicy(decision.allowTechnicalClients());
    }

    /** Current time with millisecond precision (as written into payloads). */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Today (UTC). */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public String tenantBpnl() {
        return tenant.tenantId();
    }

    public Clock clock() {
        return clock;
    }

    /** Loads a case of the tenant or fails with {@code NOT_FOUND}. */
    public ApprovalCase load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Approval case", id));
    }

    /** Saves the case and publishes the collected changes as domain events. */
    public ApprovalCase save(ApprovalCase approvalCase) {
        List<CaseChange> changes = approvalCase.pullChanges();
        ApprovalCase saved = repository.save(approvalCase);
        events.publish(saved, changes);
        return saved;
    }

    public ApprovalCaseRepository repository() {
        return repository;
    }
}
