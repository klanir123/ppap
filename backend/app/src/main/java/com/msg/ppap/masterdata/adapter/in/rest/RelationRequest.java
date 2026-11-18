package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.RelationCommand;
import com.msg.ppap.masterdata.domain.ExchangeMode;
import com.msg.ppap.masterdata.domain.PartnerRole;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create or replace a relation.
 *
 * @param exchangeMode defaults to {@code PUSH_NOTIFY_PULL_PAYLOAD}
 * @param policyProfile defaults to {@code default}
 * @param active {@code null} keeps the current state (new relations are active)
 */
public record RelationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerBpnl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) PartnerRole partnerRole,
        ExchangeMode exchangeMode,
        String policyProfile,
        Boolean active) {

    RelationCommand toCommand() {
        return new RelationCommand(
                partnerBpnl,
                partnerRole == null ? null : partnerRole.name(),
                exchangeMode == null ? null : exchangeMode.name(),
                policyProfile,
                active);
    }
}
