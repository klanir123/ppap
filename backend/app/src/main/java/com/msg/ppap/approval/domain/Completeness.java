package com.msg.ppap.approval.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completeness of a revision against the request (contract „Vollständigkeitsregeln“).
 *
 * @param issuesByElement issues per element code (only elements with issues)
 * @param hints non-blocking findings of the whole revision (e.g. {@link #SUB_SUPPLIER_PENDING}); they never make a
 *     revision incomplete
 */
public record Completeness(
        int requiredTotal, int requiredDone, Map<String, List<Issue>> issuesByElement, List<Issue> hints) {

    public static final String MISSING_SELF_ASSESSMENT = "MISSING_SELF_ASSESSMENT";
    public static final String COMMENT_REQUIRED = "COMMENT_REQUIRED";
    public static final String DOCUMENT_REQUIRED = "DOCUMENT_REQUIRED";
    public static final String VALUES_REQUIRED = "VALUES_REQUIRED";
    public static final String VALUE_SCHEMA_VIOLATION = "VALUE_SCHEMA_VIOLATION";

    /**
     * Hint (not a blocker, UC-09): the case has sub-supplier requests that are not decided yet; the supplier decides
     * whether to submit without the sub-supplier approval (TODO(EG)).
     */
    public static final String SUB_SUPPLIER_PENDING = "SUB_SUPPLIER_PENDING";

    public Completeness {
        hints = hints == null ? List.of() : List.copyOf(hints);
    }

    public Completeness(int requiredTotal, int requiredDone, Map<String, List<Issue>> issuesByElement) {
        this(requiredTotal, requiredDone, issuesByElement, List.of());
    }

    /** Copy with the given (additional) hints. */
    public Completeness withHints(List<Issue> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        List<Issue> all = new ArrayList<>(hints);
        all.addAll(additional);
        return new Completeness(requiredTotal, requiredDone, issuesByElement, all);
    }

    /** An issue of one element; {@code pointer} is set for value schema violations. */
    public record Issue(String elementCode, String code, String message, String pointer) {}

    public boolean complete() {
        return issuesByElement.isEmpty();
    }

    /** Issues of one element. */
    public List<Issue> issues(String code) {
        return issuesByElement.getOrDefault(code, List.of());
    }

    /** All issues in element order. */
    public List<Issue> issues() {
        return issuesByElement.values().stream().flatMap(List::stream).toList();
    }

    /**
     * Evaluates the rules: every {@code REQUIRED} element (and every {@code ON_REQUEST} element once filled) needs a
     * self assessment; {@code NOK}/{@code NA} need a comment; document evidence (unless {@code NA}) needs at least one
     * document; value evidence with a value schema (unless {@code NA}) needs valid values.
     */
    public static Completeness evaluate(
            List<RequestedElement> requested, Map<String, ElementEvidence> evidence, ValueValidator validator) {
        int requiredTotal = 0;
        int requiredDone = 0;
        Map<String, List<Issue>> issues = new LinkedHashMap<>();
        for (RequestedElement element : requested) {
            ElementEvidence ev = evidence.getOrDefault(element.code(), ElementEvidence.empty(element));
            boolean required = element.requirement() == Requirement.REQUIRED;
            List<Issue> found = required || ev.filled() ? check(element, ev, validator) : List.of();
            if (required) {
                requiredTotal++;
                if (found.isEmpty()) {
                    requiredDone++;
                }
            }
            if (!found.isEmpty()) {
                issues.put(element.code(), found);
            }
        }
        return new Completeness(requiredTotal, requiredDone, issues);
    }

    static List<Issue> check(RequestedElement element, ElementEvidence ev, ValueValidator validator) {
        List<Issue> issues = new ArrayList<>();
        String code = element.code();
        SelfAssessment assessment = ev.selfAssessment();
        if (assessment == null) {
            issues.add(new Issue(code, MISSING_SELF_ASSESSMENT, "self assessment missing", null));
        } else if (assessment != SelfAssessment.OK && ev.comment() == null) {
            issues.add(new Issue(code, COMMENT_REQUIRED, "comment required for " + assessment, null));
        }
        boolean applicable = assessment != SelfAssessment.NA;
        EvidenceType type = element.snapshot().evidenceType();
        if (applicable && type.needsDocument() && ev.documents().isEmpty()) {
            issues.add(new Issue(code, DOCUMENT_REQUIRED, "at least one document required", null));
        }
        Map<String, Object> schema = element.snapshot().valueSchema();
        if (applicable && type.needsValues() && schema != null) {
            if (ev.values() == null) {
                issues.add(new Issue(code, VALUES_REQUIRED, "structured values required", null));
            } else {
                for (ValueValidator.Violation violation : validator.validate(schema, ev.values())) {
                    issues.add(new Issue(code, VALUE_SCHEMA_VIOLATION, violation.message(), violation.pointer()));
                }
            }
        }
        return issues;
    }
}
