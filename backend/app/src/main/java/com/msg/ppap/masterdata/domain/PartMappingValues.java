package com.msg.ppap.masterdata.domain;

import com.msg.ppap.platform.domain.Bpns;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated attributes of a {@link PartMapping}.
 *
 * @param relationId relation the mapping belongs to
 * @param partId own part
 * @param partnerPartNumber the partner's part number
 * @param ownSiteBpns own site (e.g. receiving or shipping plant)
 * @param partnerSiteBpns partner site
 */
public record PartMappingValues(
        UUID relationId, UUID partId, PartNumber partnerPartNumber, Bpns ownSiteBpns, Bpns partnerSiteBpns) {

    public PartMappingValues {
        Objects.requireNonNull(relationId, "relationId");
    }

    /** Parses and validates raw input; the part is resolved by the application service. */
    public static PartMappingValues parse(
            UUID relationId, UUID partId, String partnerPartNumber, String ownSiteBpns, String partnerSiteBpns) {
        var v = new Validation();
        if (partId == null) {
            v.add("partId", ViolationCodes.REQUIRED, "must not be blank");
        }
        var values = new PartMappingValues(
                relationId,
                partId,
                v.required(
                        "partnerPartNumber", partnerPartNumber, PartNumber::new, "1-64 characters of [A-Za-z0-9._- /]"),
                v.optional("ownSiteBpns", ownSiteBpns, Bpns::new, Bpns.PATTERN),
                v.optional("partnerSiteBpns", partnerSiteBpns, Bpns::new, Bpns.PATTERN));
        v.throwIfInvalid();
        return values;
    }
}
