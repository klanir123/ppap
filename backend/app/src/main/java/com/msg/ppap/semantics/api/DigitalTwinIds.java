package com.msg.ppap.semantics.api;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic identifiers of the PPAP digital twin (docs/api/pull-api.md, „Digital Twin“): one shell per approval
 * case, one {@code PpapSubmission} submodel descriptor per submitted revision. Supplier and customer derive the same
 * ids from the exchange id, so the customer can verify what it pulls.
 */
public final class DigitalTwinIds {

    /** {@code specificAssetId} name carrying the exchange id (lookup key of the customer). */
    public static final String EXCHANGE_ID_KEY = "ppapExchangeId";

    /** Prefix of the per-customer submodel asset ({@code ppap-submission-<BPNL>}). */
    public static final String SUBMISSION_ASSET_PREFIX = "ppap-submission-";

    private DigitalTwinIds() {}

    /** Shell id and {@code globalAssetId}: {@code urn:uuid:<exchangeId>}. */
    public static String shellId(UUID exchangeId) {
        return "urn:uuid:" + Objects.requireNonNull(exchangeId, "exchangeId");
    }

    /** Submodel id of a revision: {@code urn:uuid:<name-based UUID (v3) of "ppap-submission:<exchangeId>:<rev>">}. */
    public static String submodelId(UUID exchangeId, int revision) {
        String name = "ppap-submission:" + Objects.requireNonNull(exchangeId, "exchangeId") + ":" + revision;
        return "urn:uuid:" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code idShort} of the shell: {@code ppap-<caseNumber>} (the supplier's case number). */
    public static String idShort(String caseNumber) {
        return "ppap-" + caseNumber;
    }

    /** EDC asset of the submodel endpoint offered to one customer. */
    public static String submissionAssetId(String customerBpnl) {
        return SUBMISSION_ASSET_PREFIX + customerBpnl;
    }
}
