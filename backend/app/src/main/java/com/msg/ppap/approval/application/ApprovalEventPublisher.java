package com.msg.ppap.approval.application;

import com.msg.ppap.approval.ApprovalCaseChanged;
import com.msg.ppap.approval.ApprovalEvent;
import com.msg.ppap.approval.CaseCancelled;
import com.msg.ppap.approval.CaseRequested;
import com.msg.ppap.approval.DecisionMade;
import com.msg.ppap.approval.ReviewStarted;
import com.msg.ppap.approval.SubmissionSubmitted;
import com.msg.ppap.approval.WorkStarted;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseChange;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.identity.TenantContext;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Maps the changes collected by the aggregate to the public domain events of the module and publishes them
 * (transactional, Spring Modulith event publication registry).
 */
@Component
public class ApprovalEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final TenantContext tenant;

    public ApprovalEventPublisher(ApplicationEventPublisher publisher, TenantContext tenant) {
        this.publisher = publisher;
        this.tenant = tenant;
    }

    public void publish(ApprovalCase c, List<CaseChange> changes) {
        changes.forEach(change -> publisher.publishEvent(toEvent(c, change)));
    }

    /** Publishes a technical change (e.g. seed import) that is not an action of the state machine. */
    public void publish(ApprovalCase c, String action, String actor, Instant at) {
        publisher.publishEvent(new ApprovalCaseChanged(
                tenant.tenantId(),
                at,
                actor,
                c.id(),
                c.caseNumber(),
                c.exchangeId(),
                c.ownRole().name(),
                c.source().name(),
                c.header().partner().bpnl(),
                null,
                c.status().name(),
                action,
                c.currentSubmission().map(s -> s.revision()).orElse(null),
                null));
    }

    ApprovalEvent toEvent(ApprovalCase c, CaseChange ch) {
        String t = tenant.tenantId();
        String own = c.ownRole().name();
        String src = c.source().name();
        String partner = c.header().partner().bpnl();
        String from = name(ch.from());
        String to = name(ch.to());
        return switch (ch.action()) {
            case SEND ->
                new CaseRequested(
                        t, ch.at(), ch.actor(), c.id(), c.caseNumber(), c.exchangeId(), own, src, partner, from, to);
            case CANCEL ->
                new CaseCancelled(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.comment());
            case START_WORK ->
                new WorkStarted(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.revision());
            case SUBMIT ->
                new SubmissionSubmitted(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.revision(),
                        hashOf(c.submission(ch.revision()).seal()),
                        modeOf(c.submission(ch.revision()).exchangeMode()));
            case START_REVIEW ->
                new ReviewStarted(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.revision());
            case DECIDE ->
                new DecisionMade(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.revision(),
                        to,
                        c.submission(ch.revision())
                                .decision()
                                .map(Decision::seal)
                                .map(ApprovalEventPublisher::hashOf)
                                .orElse(null));
            default ->
                new ApprovalCaseChanged(
                        t,
                        ch.at(),
                        ch.actor(),
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        own,
                        src,
                        partner,
                        from,
                        to,
                        ch.action().name(),
                        ch.revision(),
                        ch.elementCode());
        };
    }

    private static String name(CaseStatus status) {
        return status == null ? null : status.name();
    }

    private static String hashOf(Seal seal) {
        return seal == null ? null : seal.payloadHash();
    }

    private static String modeOf(com.msg.ppap.approval.domain.ExchangeMode mode) {
        return mode == null ? null : mode.name();
    }
}
