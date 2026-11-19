package com.msg.ppap.approval.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Header data of a case.
 *
 * @param relationId master data relation to the partner ({@code null} for received cases without relation)
 * @param partnerPartNumber the partner's part number
 * @param externalReference reference of the customer (e.g. e-mail or portal id) for manual cases
 */
public record CaseHeader(
        PartnerRef partner,
        UUID relationId,
        PartRef part,
        String partnerPartNumber,
        CatalogRef reason,
        CatalogRef level,
        LocalDate dueDate,
        String remarks,
        String externalReference) {

    public CaseHeader {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(dueDate, "dueDate");
        remarks = remarks == null || remarks.isBlank() ? null : remarks;
        externalReference = externalReference == null || externalReference.isBlank() ? null : externalReference;
        partnerPartNumber = partnerPartNumber == null || partnerPartNumber.isBlank() ? null : partnerPartNumber;
    }
}
