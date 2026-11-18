package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.RelationValues;

/**
 * Raw input for creating or updating a customer-supplier relation.
 *
 * @param active desired active flag; {@code null} keeps the current state (new objects are active)
 */
public record RelationCommand(
        String partnerBpnl, String partnerRole, String exchangeMode, String policyProfile, Boolean active) {

    RelationValues toValues() {
        return RelationValues.parse(partnerBpnl, partnerRole, exchangeMode, policyProfile);
    }
}
