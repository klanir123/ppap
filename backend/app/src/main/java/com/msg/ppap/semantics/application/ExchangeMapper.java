package com.msg.ppap.semantics.application;

import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.api.WithdrawalMessage;
import tools.jackson.databind.JsonNode;

/**
 * Versioned mapping between version-neutral exchange messages and the payload JSON of one exchange format version
 * (ADR-0003: {@code ExchangeModelMapper}). Payloads passed to the {@code to*} methods have been validated against the
 * schema of this version.
 */
public interface ExchangeMapper {

    /** Exchange format version, e.g. {@code 0.1.0}. */
    String version();

    JsonNode toPayload(RequestMessage message);

    RequestMessage toRequest(JsonNode payload);

    JsonNode toPayload(SubmissionMessage message);

    SubmissionMessage toSubmission(JsonNode payload);

    JsonNode toPayload(DecisionMessage message);

    DecisionMessage toDecision(JsonNode payload);

    JsonNode toPayload(StatusUpdateMessage message);

    StatusUpdateMessage toStatusUpdate(JsonNode payload);

    JsonNode toPayload(WithdrawalMessage message);

    WithdrawalMessage toWithdrawal(JsonNode payload);
}
