package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.PartnerRef;
import java.time.Instant;
import java.util.UUID;

/**
 * Entry of the activity feed (UC-11): a timeline entry of any case of the tenant.
 *
 * @param actor recorded actor (user name, {@code client:<id>}, {@code partner:<BPNL>}, {@code system:*})
 * @param actorDisplay display name: the partner's name instead of {@code partner:<BPNL>}
 */
public record ActivityView(
        Instant at,
        UUID caseId,
        String caseNumber,
        String action,
        String actor,
        String actorDisplay,
        PartnerRef partner,
        String fromStatus,
        String toStatus) {}
