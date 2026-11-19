package com.msg.ppap.approval.domain;

import java.util.List;
import java.util.Objects;

/**
 * Evidence of one element in a revision.
 *
 * @param values structured values as JSON value tree (map, list, scalar) or {@code null}
 */
public record ElementEvidence(
        String code,
        Requirement requirement,
        SelfAssessment selfAssessment,
        Object values,
        String comment,
        List<DocumentRef> documents) {

    public ElementEvidence {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(requirement, "requirement");
        comment = comment == null || comment.isBlank() ? null : comment;
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** Empty evidence for a requested element. */
    public static ElementEvidence empty(RequestedElement element) {
        return new ElementEvidence(element.code(), element.requirement(), null, null, null, List.of());
    }

    /** Whether anything has been entered. */
    public boolean filled() {
        return selfAssessment != null || values != null || comment != null || !documents.isEmpty();
    }

    ElementEvidence withContent(SelfAssessment newAssessment, Object newValues, String newComment) {
        return new ElementEvidence(code, requirement, newAssessment, newValues, newComment, documents);
    }

    ElementEvidence withDocuments(List<DocumentRef> newDocuments) {
        return new ElementEvidence(code, requirement, selfAssessment, values, comment, newDocuments);
    }
}
