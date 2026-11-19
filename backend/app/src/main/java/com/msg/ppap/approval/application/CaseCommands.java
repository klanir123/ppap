package com.msg.ppap.approval.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Commands of the case use cases (raw input, validated by the services). */
public final class CaseCommands {

    private CaseCommands() {}

    /** Customer creates a draft. */
    public record Create(
            UUID partId,
            UUID relationId,
            String reasonCode,
            String levelCode,
            LocalDate dueDate,
            String remarks,
            List<ElementInput> elements) {}

    /** Supplier captures a request manually. */
    public record Manual(
            String partnerBpnl,
            UUID partId,
            String partnerPartNumber,
            String reasonCode,
            String levelCode,
            LocalDate dueDate,
            String remarks,
            List<ElementInput> elements,
            String externalReference) {}

    /** Changes the draft header. */
    public record Update(String reasonCode, String levelCode, LocalDate dueDate, String remarks) {}

    /** Element evidence. */
    public record Evidence(String selfAssessment, Object values, String comment) {}

    /** Review finding. */
    public record Finding(String result, String comment) {}

    /** Decision. */
    public record Decide(
            String result, List<String> conditions, LocalDate conditionDueDate, String comment, Boolean confirmation) {}
}
