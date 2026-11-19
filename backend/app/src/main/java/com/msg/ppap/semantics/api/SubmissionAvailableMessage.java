package com.msg.ppap.semantics.api;

import java.util.UUID;

/**
 * Notification {@code PpapSubmissionAvailable} (exchange mode {@code PUSH_NOTIFY_PULL_PAYLOAD}): a submitted revision
 * is registered in the supplier's Digital Twin Registry and can be pulled by the customer.
 *
 * @param exchangeId exchange id of the case
 * @param revision submitted revision
 * @param payloadHash {@code sha256:<hex>} of the canonical {@code PpapSubmission} payload to pull
 * @param shellId id of the digital twin ({@link DigitalTwinIds#shellId})
 * @param submodelId id of the submodel descriptor of the revision ({@link DigitalTwinIds#submodelId})
 */
public record SubmissionAvailableMessage(
        UUID exchangeId, int revision, String payloadHash, String shellId, String submodelId) {}
