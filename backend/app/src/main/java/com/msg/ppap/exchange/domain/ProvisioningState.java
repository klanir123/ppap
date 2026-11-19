package com.msg.ppap.exchange.domain;

import java.time.Instant;
import java.util.List;

/**
 * Result of the last provisioning run.
 *
 * @param assets provisioned asset ids
 * @param partners BPNLs in the access group
 * @param ok whether the last run succeeded
 * @param lastRun time of the last run ({@code null} before the first)
 * @param error failure of the last run
 */
public record ProvisioningState(List<String> assets, List<String> partners, boolean ok, Instant lastRun, String error) {

    public static final ProvisioningState INITIAL = new ProvisioningState(List.of(), List.of(), false, null, null);

    public ProvisioningState {
        assets = assets == null ? List.of() : List.copyOf(assets);
        partners = partners == null ? List.of() : List.copyOf(partners);
    }
}
