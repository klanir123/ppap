package com.msg.ppap.semantics.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Status change on the sender's side, exchange model {@code PpapStatusUpdate}: the supplier started working on a
 * revision ({@code IN_PROGRESS}) or the customer started the review ({@code IN_REVIEW}).
 *
 * @param exchangeId exchange id of the case
 * @param status {@code IN_PROGRESS} or {@code IN_REVIEW}
 * @param revision revision the status refers to
 * @param at time of the change
 * @param by acting user
 */
public record StatusUpdateMessage(UUID exchangeId, String status, int revision, Instant at, String by) {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String IN_REVIEW = "IN_REVIEW";
}
