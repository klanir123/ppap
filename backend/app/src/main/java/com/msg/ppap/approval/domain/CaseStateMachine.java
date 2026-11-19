package com.msg.ppap.approval.domain;

import static com.msg.ppap.approval.domain.CaseAction.CANCEL;
import static com.msg.ppap.approval.domain.CaseAction.CAPTURE_MANUAL;
import static com.msg.ppap.approval.domain.CaseAction.CREATE;
import static com.msg.ppap.approval.domain.CaseAction.DECIDE;
import static com.msg.ppap.approval.domain.CaseAction.EDIT;
import static com.msg.ppap.approval.domain.CaseAction.EDIT_SUBMISSION;
import static com.msg.ppap.approval.domain.CaseAction.NEW_REVISION;
import static com.msg.ppap.approval.domain.CaseAction.RECEIVE_DECISION;
import static com.msg.ppap.approval.domain.CaseAction.RECEIVE_REQUEST;
import static com.msg.ppap.approval.domain.CaseAction.RECEIVE_SUBMISSION;
import static com.msg.ppap.approval.domain.CaseAction.REVIEW;
import static com.msg.ppap.approval.domain.CaseAction.SEND;
import static com.msg.ppap.approval.domain.CaseAction.START_REVIEW;
import static com.msg.ppap.approval.domain.CaseAction.START_WORK;
import static com.msg.ppap.approval.domain.CaseAction.SUBMIT;
import static com.msg.ppap.approval.domain.CaseStatus.APPROVED;
import static com.msg.ppap.approval.domain.CaseStatus.CANCELLED;
import static com.msg.ppap.approval.domain.CaseStatus.CONDITIONALLY_APPROVED;
import static com.msg.ppap.approval.domain.CaseStatus.DRAFT;
import static com.msg.ppap.approval.domain.CaseStatus.IN_PROGRESS;
import static com.msg.ppap.approval.domain.CaseStatus.IN_REVIEW;
import static com.msg.ppap.approval.domain.CaseStatus.REJECTED;
import static com.msg.ppap.approval.domain.CaseStatus.REQUESTED;
import static com.msg.ppap.approval.domain.CaseStatus.SUBMITTED;
import static com.msg.ppap.approval.domain.UserRole.CUSTOMER_QUALITY_ENGINEER;
import static com.msg.ppap.approval.domain.UserRole.EXCHANGE;
import static com.msg.ppap.approval.domain.UserRole.SUPPLIER_QUALITY_MANAGER;
import static com.msg.ppap.approval.domain.UserRole.SYSTEM_INTEGRATION;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit transition table of approval cases (docs/api/approval-api.md, „Zustandsmaschine“). Each rule names the
 * action, the role of the own tenant in the case, the allowed source statuses (empty = creation), the target
 * statuses, the user roles and an optional source guard.
 *
 * <p>Checks are applied in a fixed order: case role ({@code WRONG_CASE_ROLE}, 409) → user role ({@code FORBIDDEN},
 * 403) → status ({@code INVALID_STATE_TRANSITION}, 409). Action-specific guards (completeness, decision rules …)
 * are checked by the aggregate afterwards.
 */
public final class CaseStateMachine {

    /**
     * A rule of the transition table.
     *
     * @param from allowed statuses; empty for creating actions
     * @param to target statuses (several for decisions); equal to {@code from} for edit actions
     * @param userRoles roles of which the actor needs at least one
     * @param manualOnly only for cases with source {@code MANUAL}
     * @param technicalByPolicy {@code SYSTEM_INTEGRATION} only if {@link CasePolicy#allowTechnicalDecisions()}
     */
    public record Rule(
            CaseAction action,
            CaseRole caseRole,
            Set<CaseStatus> from,
            Set<CaseStatus> to,
            Set<UserRole> userRoles,
            boolean manualOnly,
            boolean technicalByPolicy) {

        public Rule {
            from = from.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(from));
            to = Set.copyOf(EnumSet.copyOf(to));
            userRoles = Set.copyOf(EnumSet.copyOf(userRoles));
        }

        /** Whether this rule creates a case. */
        public boolean creating() {
            return from.isEmpty();
        }

        /** Whether the rule changes the status. */
        public boolean transition() {
            return creating() || !from.equals(to);
        }

        boolean permits(Actor actor, CasePolicy policy) {
            for (UserRole role : actor.roles()) {
                if (userRoles.contains(role)
                        && !(role == SYSTEM_INTEGRATION && technicalByPolicy && !policy.allowTechnicalDecisions())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Set<UserRole> CUSTOMER_USERS = EnumSet.of(CUSTOMER_QUALITY_ENGINEER, SYSTEM_INTEGRATION);
    private static final Set<UserRole> SUPPLIER_USERS = EnumSet.of(SUPPLIER_QUALITY_MANAGER, SYSTEM_INTEGRATION);
    private static final Set<CaseStatus> DECISIONS = EnumSet.of(APPROVED, CONDITIONALLY_APPROVED, REJECTED);

    /** The transition table. */
    public static final List<Rule> RULES = List.of(
            rule(CREATE, CaseRole.CUSTOMER, Set.of(), Set.of(DRAFT), CUSTOMER_USERS),
            rule(CAPTURE_MANUAL, CaseRole.SUPPLIER, Set.of(), Set.of(REQUESTED), SUPPLIER_USERS),
            rule(RECEIVE_REQUEST, CaseRole.SUPPLIER, Set.of(), Set.of(REQUESTED), Set.of(EXCHANGE)),
            rule(EDIT, CaseRole.CUSTOMER, Set.of(DRAFT), Set.of(DRAFT), CUSTOMER_USERS),
            rule(SEND, CaseRole.CUSTOMER, Set.of(DRAFT), Set.of(REQUESTED), CUSTOMER_USERS),
            rule(CANCEL, CaseRole.CUSTOMER, Set.of(DRAFT, REQUESTED), Set.of(CANCELLED), CUSTOMER_USERS),
            new Rule(
                    CANCEL,
                    CaseRole.SUPPLIER,
                    Set.of(DRAFT, REQUESTED),
                    Set.of(CANCELLED),
                    SUPPLIER_USERS,
                    true,
                    false),
            rule(START_WORK, CaseRole.SUPPLIER, Set.of(REQUESTED), Set.of(IN_PROGRESS), SUPPLIER_USERS),
            rule(EDIT_SUBMISSION, CaseRole.SUPPLIER, Set.of(IN_PROGRESS), Set.of(IN_PROGRESS), SUPPLIER_USERS),
            rule(SUBMIT, CaseRole.SUPPLIER, Set.of(IN_PROGRESS), Set.of(SUBMITTED), SUPPLIER_USERS),
            rule(
                    RECEIVE_SUBMISSION,
                    CaseRole.CUSTOMER,
                    Set.of(REQUESTED, IN_PROGRESS, REJECTED, CONDITIONALLY_APPROVED),
                    Set.of(SUBMITTED),
                    Set.of(EXCHANGE)),
            rule(
                    START_REVIEW,
                    CaseRole.CUSTOMER,
                    Set.of(SUBMITTED),
                    Set.of(IN_REVIEW),
                    Set.of(CUSTOMER_QUALITY_ENGINEER)),
            new Rule(REVIEW, CaseRole.CUSTOMER, Set.of(IN_REVIEW), Set.of(IN_REVIEW), CUSTOMER_USERS, false, true),
            new Rule(DECIDE, CaseRole.CUSTOMER, Set.of(IN_REVIEW), DECISIONS, CUSTOMER_USERS, false, true),
            rule(RECEIVE_DECISION, CaseRole.SUPPLIER, Set.of(SUBMITTED, IN_REVIEW), DECISIONS, Set.of(EXCHANGE)),
            rule(
                    NEW_REVISION,
                    CaseRole.SUPPLIER,
                    Set.of(REJECTED, CONDITIONALLY_APPROVED),
                    Set.of(IN_PROGRESS),
                    SUPPLIER_USERS));

    private CaseStateMachine() {}

    private static Rule rule(
            CaseAction action, CaseRole role, Set<CaseStatus> from, Set<CaseStatus> to, Set<UserRole> users) {
        return new Rule(action, role, from, to, users, false, false);
    }

    /**
     * Checks an action on an existing case.
     *
     * @return the matching rule
     */
    public static Rule check(
            CaseAction action, CaseRole ownRole, CaseSource source, CaseStatus status, Actor actor, CasePolicy policy) {
        Rule rule = ruleFor(action, ownRole, source);
        if (!rule.permits(actor, policy)) {
            throw ApprovalErrors.forbidden(action);
        }
        if (rule.creating() || !rule.from().contains(status)) {
            throw ApprovalErrors.invalidTransition(status, action);
        }
        return rule;
    }

    /** Checks a creating action. */
    public static Rule checkCreate(CaseAction action, CaseRole ownRole, CaseSource source, Actor actor) {
        Rule rule = ruleFor(action, ownRole, source);
        if (!rule.creating()) {
            throw ApprovalErrors.invalidTransition(null, action);
        }
        if (!rule.permits(actor, CasePolicy.DEFAULT)) {
            throw ApprovalErrors.forbidden(action);
        }
        return rule;
    }

    /** User actions (no creation, no inbound) the actor may perform now, in table order. */
    public static List<CaseAction> allowedActions(
            CaseRole ownRole, CaseSource source, CaseStatus status, Actor actor, CasePolicy policy) {
        List<CaseAction> allowed = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.creating()
                    || rule.action().inbound()
                    || rule.caseRole() != ownRole
                    || (rule.manualOnly() && source != CaseSource.MANUAL)
                    || !rule.from().contains(status)
                    || !rule.permits(actor, policy)) {
                continue;
            }
            if (!allowed.contains(rule.action())) {
                allowed.add(rule.action());
            }
        }
        allowed.sort(Comparator.naturalOrder());
        return List.copyOf(allowed);
    }

    private static Rule ruleFor(CaseAction action, CaseRole ownRole, CaseSource source) {
        List<Rule> candidates = RULES.stream().filter(r -> r.action() == action).toList();
        Rule rule = candidates.stream()
                .filter(r -> r.caseRole() == ownRole)
                .findFirst()
                .orElseThrow(() -> ApprovalErrors.wrongCaseRole(ownRole, action, "action of the other side"));
        if (rule.manualOnly() && source != CaseSource.MANUAL) {
            throw ApprovalErrors.wrongCaseRole(ownRole, action, "only manually captured requests");
        }
        return rule;
    }
}
