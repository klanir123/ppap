package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.domain.ProvisioningState;

/**
 * Overall exchange status for the cockpit.
 *
 * @param pending outbound messages waiting for (re)delivery
 * @param failed outbound messages given up
 * @param rejected inbound messages rejected
 */
public record ExchangeStatus(
        ConnectorHealth edc, ProvisioningState provisioning, long pending, long failed, long rejected) {}
