package com.msg.ppap.approval.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The customer's request: requested elements with snapshots and attachments.
 *
 * @param requestedAt time of sending ({@code null} while DRAFT)
 * @param requestedBy sender ({@code null} while DRAFT)
 */
public record ApprovalRequest(
        Instant requestedAt, String requestedBy, List<RequestedElement> elements, List<DocumentRef> attachments) {

    public ApprovalRequest {
        elements = elements == null ? List.of() : List.copyOf(elements);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public Optional<RequestedElement> element(String code) {
        return elements.stream().filter(e -> e.code().equals(code)).findFirst();
    }

    ApprovalRequest withElements(List<RequestedElement> newElements) {
        return new ApprovalRequest(requestedAt, requestedBy, newElements, attachments);
    }

    ApprovalRequest withAttachments(List<DocumentRef> newAttachments) {
        return new ApprovalRequest(requestedAt, requestedBy, elements, newAttachments);
    }

    ApprovalRequest sent(Instant at, String by) {
        return new ApprovalRequest(at, by, elements, attachments);
    }
}
