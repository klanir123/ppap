package com.msg.ppap.approval.application;

import com.msg.ppap.approval.config.DecisionProperties;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.ElementFinding;
import com.msg.ppap.approval.domain.FindingResult;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the customer's review and decision (UC-07). */
@Service
@Transactional
public class ReviewService {

    static final int TEXT_MAX = 4000;

    private final ApprovalContext context;
    private final ApprovalCaseService cases;
    private final PayloadSealing sealing;
    private final DecisionProperties properties;

    public ReviewService(
            ApprovalContext context, ApprovalCaseService cases, PayloadSealing sealing, DecisionProperties properties) {
        this.context = context;
        this.cases = cases;
        this.sealing = sealing;
        this.properties = properties;
    }

    /** SUBMITTED → IN_REVIEW after verifying payload hash and signature of the revision. */
    public CaseView startReview(UUID caseId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        // the revision was signed by the supplier (partner) – verify against the partner's keys
        String signer = c.header().partner().bpnl();
        c.startReview(seal -> sealing.verify(seal, signer), context.policy(), actor, context.now());
        return cases.view(context.save(c), actor);
    }

    /** Intermediate review result of an element (not signed). */
    public CaseView recordFinding(UUID caseId, String code, CaseCommands.Finding command, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        var v = new Validation();
        FindingResult result = v.requiredEnum("result", command.result(), FindingResult.class);
        String comment = v.optional("comment", command.comment(), TEXT_MAX);
        v.throwIfInvalid();
        c.recordFinding(new ElementFinding(code, result, comment), context.policy(), actor, context.now());
        return cases.view(context.save(c), actor);
    }

    /** Signed decision; optionally requires a recent interactive login. */
    public Decision decide(UUID caseId, CaseCommands.Decide command, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(caseId, expectedVersion);
        var v = new Validation();
        DecisionResult result = v.requiredEnum("result", command.result(), DecisionResult.class);
        String comment = v.optional("comment", command.comment(), TEXT_MAX);
        if (command.conditions() != null) {
            for (int i = 0; i < command.conditions().size(); i++) {
                v.optional("conditions[" + i + "].text", command.conditions().get(i), TEXT_MAX);
            }
        }
        v.throwIfInvalid();
        checkRecentAuthentication(actor);
        Decision decision = c.decide(
                result,
                command.conditions(),
                command.conditionDueDate(),
                comment,
                Boolean.TRUE.equals(command.confirmation()),
                sealing::sealDecision,
                context.policy(),
                actor,
                context.now(),
                context.today());
        context.save(c);
        return decision;
    }

    /** Canonical {@code PpapDecision} of a revision. */
    @Transactional(readOnly = true)
    public Seal decisionPayload(UUID caseId, int revision) {
        return context.load(caseId)
                .submission(revision)
                .decision()
                .map(Decision::seal)
                .orElseThrow(() -> new NotFoundException("Decision of revision", revision));
    }

    /** Version of the case after the decision (ETag). */
    @Transactional(readOnly = true)
    public Long version(UUID caseId) {
        return context.load(caseId).version();
    }

    void checkRecentAuthentication(Actor actor) {
        Long maxAge = properties.maxAuthAgeSeconds();
        if (maxAge == null || actor.technical()) {
            return;
        }
        Instant authTime = actor.authTime();
        if (authTime == null || authTime.isBefore(context.clock().instant().minus(Duration.ofSeconds(maxAge)))) {
            throw new RecentAuthRequiredException(maxAge);
        }
    }

    private ApprovalCase loadChecked(UUID id, Long expectedVersion) {
        ApprovalCase c = context.load(id);
        c.checkVersion(expectedVersion);
        return c;
    }
}
