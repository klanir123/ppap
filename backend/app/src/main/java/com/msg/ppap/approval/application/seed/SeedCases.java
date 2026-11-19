package com.msg.ppap.approval.application.seed;

import java.util.List;
import java.util.UUID;

/**
 * Structure of {@code approval-cases.json} (docs/api/approval-api.md, „Seed-Format“). Dates are {@code ±Nd} relative
 * to today or ISO-8601.
 */
public record SeedCases(List<SeedCase> cases) {

    public SeedCases {
        cases = cases == null ? List.of() : cases;
    }

    /** A case of one side. */
    public record SeedCase(
            UUID exchangeId,
            String caseNumber,
            String ownRole,
            String source,
            String status,
            String partnerBpnl,
            String partNumber,
            String partnerPartNumber,
            String reasonCode,
            String levelCode,
            String dueDate,
            String remarks,
            String requestedAt,
            String requestedBy,
            List<Element> elements,
            List<Timeline> timeline,
            List<SeedSubmission> submissions) {

        public SeedCase {
            elements = elements == null ? List.of() : elements;
            timeline = timeline == null ? List.of() : timeline;
            submissions = submissions == null ? List.of() : submissions;
        }
    }

    /** Requested element. */
    public record Element(String code, String requirement, String note) {}

    /** Timeline entry ({@code from} is {@code null} for creations). */
    public record Timeline(String at, String actor, String from, String to, String action, String comment) {}

    /** Revision. */
    public record SeedSubmission(
            int revision,
            String status,
            String submittedAt,
            String submittedBy,
            List<Evidence> elements,
            SeedDecision decision) {

        public SeedSubmission {
            elements = elements == null ? List.of() : elements;
        }
    }

    /** Element evidence; {@code documents} are file names in the seed documents folder. */
    public record Evidence(String code, String selfAssessment, Object values, String comment, List<String> documents) {

        public Evidence {
            documents = documents == null ? List.of() : documents;
        }
    }

    /** Decision of a legacy revision. */
    public record SeedDecision(
            String result,
            List<Condition> conditions,
            String conditionDueDate,
            String comment,
            List<Finding> elementFindings,
            String decidedAt,
            String decidedBy) {

        public SeedDecision {
            conditions = conditions == null ? List.of() : conditions;
            elementFindings = elementFindings == null ? List.of() : elementFindings;
        }
    }

    /** Condition. */
    public record Condition(String text) {}

    /** Element finding. */
    public record Finding(String code, String result, String comment) {}
}
