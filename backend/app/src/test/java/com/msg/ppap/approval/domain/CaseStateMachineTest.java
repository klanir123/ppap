package com.msg.ppap.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.PpapException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exhaustive, table-driven test of the transition table: every action × own role × source × status × user role ×
 * policy is checked against an independent specification (the contract table), including all forbidden
 * combinations and the order of the checks (case role → user role → status).
 */
class CaseStateMachineTest {

    /** Independent specification: action | own role | from statuses (- = creation) | user roles | flags. */
    static final List<String> SPEC = List.of(
            "CREATE             | CUSTOMER | -                                               | CQE,SI | ",
            "CAPTURE_MANUAL     | SUPPLIER | -                                               | SQM,SI | ",
            "RECEIVE_REQUEST    | SUPPLIER | -                                               | EX     | ",
            "EDIT       | CUSTOMER | DRAFT                                           | CQE,SI | ",
            "SEND               | CUSTOMER | DRAFT                                           | CQE,SI | ",
            "CANCEL             | CUSTOMER | DRAFT,REQUESTED                                 | CQE,SI | ",
            "CANCEL             | SUPPLIER | DRAFT,REQUESTED                                 | SQM,SI | MANUAL",
            "START_WORK         | SUPPLIER | REQUESTED                                       | SQM,SI | ",
            "EDIT_SUBMISSION    | SUPPLIER | IN_PROGRESS                                     | SQM,SI | ",
            "SUBMIT             | SUPPLIER | IN_PROGRESS                                     | SQM,SI | ",
            "RECEIVE_SUBMISSION | CUSTOMER | REQUESTED,IN_PROGRESS,REJECTED,CONDITIONALLY_APPROVED | EX | ",
            "START_REVIEW       | CUSTOMER | SUBMITTED                                       | CQE    | ",
            "REVIEW     | CUSTOMER | IN_REVIEW                                       | CQE,SI | POLICY",
            "DECIDE             | CUSTOMER | IN_REVIEW                                       | CQE,SI | POLICY",
            "RECEIVE_DECISION   | SUPPLIER | SUBMITTED,IN_REVIEW                             | EX     | ",
            "NEW_REVISION       | SUPPLIER | REJECTED,CONDITIONALLY_APPROVED                 | SQM,SI | ");

    enum Outcome {
        OK,
        WRONG_CASE_ROLE,
        FORBIDDEN,
        INVALID_STATE_TRANSITION
    }

    record SpecRule(CaseAction action, CaseRole role, Set<CaseStatus> from, Set<UserRole> roles, String flag) {}

    static List<SpecRule> spec() {
        List<SpecRule> rules = new ArrayList<>();
        for (String line : SPEC) {
            String[] p = Arrays.stream(line.split("\\|")).map(String::strip).toArray(String[]::new);
            Set<CaseStatus> from = p[2].equals("-")
                    ? Set.of()
                    : Arrays.stream(p[2].split(",")).map(CaseStatus::valueOf).collect(Collectors.toSet());
            Set<UserRole> roles = Arrays.stream(p[3].split(","))
                    .map(r -> switch (r) {
                        case "CQE" -> UserRole.CUSTOMER_QUALITY_ENGINEER;
                        case "SQM" -> UserRole.SUPPLIER_QUALITY_MANAGER;
                        case "SI" -> UserRole.SYSTEM_INTEGRATION;
                        default -> UserRole.EXCHANGE;
                    })
                    .collect(Collectors.toSet());
            rules.add(new SpecRule(
                    CaseAction.valueOf(p[0]), CaseRole.valueOf(p[1]), from, roles, p.length > 4 ? p[4] : ""));
        }
        return rules;
    }

    static Outcome expected(
            CaseAction action, CaseRole role, CaseSource source, CaseStatus status, UserRole user, boolean policy) {
        SpecRule rule = spec().stream()
                .filter(r -> r.action() == action && r.role() == role)
                .findFirst()
                .orElse(null);
        if (rule == null || ("MANUAL".equals(rule.flag()) && source != CaseSource.MANUAL)) {
            return Outcome.WRONG_CASE_ROLE;
        }
        boolean permitted = rule.roles().contains(user)
                && !(user == UserRole.SYSTEM_INTEGRATION && "POLICY".equals(rule.flag()) && !policy);
        if (!permitted) {
            return Outcome.FORBIDDEN;
        }
        if (rule.from().isEmpty() || !rule.from().contains(status)) {
            return Outcome.INVALID_STATE_TRANSITION;
        }
        return Outcome.OK;
    }

    static Stream<Arguments> combinations() {
        List<Arguments> all = new ArrayList<>();
        for (CaseAction action : CaseAction.values()) {
            for (CaseRole role : CaseRole.values()) {
                for (CaseSource source : CaseSource.values()) {
                    for (CaseStatus status : CaseStatus.values()) {
                        for (UserRole user : UserRole.values()) {
                            for (boolean policy : new boolean[] {false, true}) {
                                all.add(Arguments.of(action, role, source, status, user, policy));
                            }
                        }
                    }
                }
            }
        }
        return all.stream();
    }

    @ParameterizedTest(name = "{0} {1} {2} {3} {4} policy={5}")
    @MethodSource("combinations")
    void checkMatchesSpecification(
            CaseAction action, CaseRole role, CaseSource source, CaseStatus status, UserRole user, boolean policy) {
        Outcome expected = expected(action, role, source, status, user, policy);
        Outcome actual;
        try {
            CaseStateMachine.check(action, role, source, status, actor(user), new CasePolicy(policy));
            actual = Outcome.OK;
        } catch (PpapException ex) {
            actual = Outcome.valueOf(ex.code());
            if (actual == Outcome.INVALID_STATE_TRANSITION) {
                assertThat(ex.kind()).isEqualTo(PpapException.Kind.CONFLICT);
                assertThat(ex.properties()).containsEntry("action", action.name());
            } else if (actual == Outcome.FORBIDDEN) {
                assertThat(ex.kind()).isEqualTo(PpapException.Kind.FORBIDDEN);
            } else {
                assertThat(ex.kind()).isEqualTo(PpapException.Kind.CONFLICT);
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} {2} {3} {4} policy={5}")
    @MethodSource("combinations")
    void allowedActionsMatchSpecification(
            CaseAction action, CaseRole role, CaseSource source, CaseStatus status, UserRole user, boolean policy) {
        boolean allowed = CaseStateMachine.allowedActions(role, source, status, actor(user), new CasePolicy(policy))
                .contains(action);
        boolean expected = !action.inbound()
                && !EnumSet.of(CaseAction.CREATE, CaseAction.CAPTURE_MANUAL).contains(action)
                && expected(action, role, source, status, user, policy) == Outcome.OK;
        assertThat(allowed).isEqualTo(expected);
    }

    @Test
    void tableMatchesSpecificationExactly() {
        assertThat(CaseStateMachine.RULES).hasSize(SPEC.size());
        for (SpecRule spec : spec()) {
            var rule = CaseStateMachine.RULES.stream()
                    .filter(r -> r.action() == spec.action() && r.caseRole() == spec.role())
                    .findFirst()
                    .orElseThrow();
            assertThat(rule.from()).isEqualTo(spec.from());
            assertThat(rule.userRoles()).isEqualTo(spec.roles());
            assertThat(rule.manualOnly()).isEqualTo("MANUAL".equals(spec.flag()));
            assertThat(rule.technicalByPolicy()).isEqualTo("POLICY".equals(spec.flag()));
        }
    }

    @Test
    void targetsOfTransitions() {
        assertThat(rule(CaseAction.SEND).to()).containsExactly(CaseStatus.REQUESTED);
        assertThat(rule(CaseAction.DECIDE).to())
                .containsExactlyInAnyOrder(CaseStatus.APPROVED, CaseStatus.CONDITIONALLY_APPROVED, CaseStatus.REJECTED);
        assertThat(rule(CaseAction.NEW_REVISION).to()).containsExactly(CaseStatus.IN_PROGRESS);
        assertThat(rule(CaseAction.EDIT).transition()).isFalse();
        assertThat(rule(CaseAction.CREATE).transition()).isTrue();
        assertThat(rule(CaseAction.CREATE).creating()).isTrue();
    }

    @Test
    void creationChecks() {
        assertThat(CaseStateMachine.checkCreate(
                                CaseAction.CREATE,
                                CaseRole.CUSTOMER,
                                CaseSource.LOCAL,
                                actor(UserRole.CUSTOMER_QUALITY_ENGINEER))
                        .action())
                .isEqualTo(CaseAction.CREATE);
        assertThatThrownBy(() -> CaseStateMachine.checkCreate(
                        CaseAction.CREATE,
                        CaseRole.CUSTOMER,
                        CaseSource.LOCAL,
                        actor(UserRole.SUPPLIER_QUALITY_MANAGER)))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThatThrownBy(() -> CaseStateMachine.checkCreate(
                        CaseAction.SEND,
                        CaseRole.CUSTOMER,
                        CaseSource.LOCAL,
                        actor(UserRole.CUSTOMER_QUALITY_ENGINEER)))
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
        assertThatThrownBy(() -> CaseStateMachine.checkCreate(
                        CaseAction.CREATE,
                        CaseRole.SUPPLIER,
                        CaseSource.LOCAL,
                        actor(UserRole.CUSTOMER_QUALITY_ENGINEER)))
                .hasFieldOrPropertyWithValue("code", "WRONG_CASE_ROLE");
    }

    @Test
    void actorWithoutRolesIsForbidden() {
        assertThatThrownBy(() -> CaseStateMachine.check(
                        CaseAction.SEND,
                        CaseRole.CUSTOMER,
                        CaseSource.LOCAL,
                        CaseStatus.DRAFT,
                        new Actor("nobody", Set.of(), false, null),
                        CasePolicy.DEFAULT))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    private static CaseStateMachine.Rule rule(CaseAction action) {
        return CaseStateMachine.RULES.stream()
                .filter(r -> r.action() == action)
                .findFirst()
                .orElseThrow();
    }

    private static Actor actor(UserRole role) {
        return new Actor("user", Set.of(role), role == UserRole.SYSTEM_INTEGRATION, null);
    }
}
