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
 * @param usagePolicies usage policy id per offered asset (phase 6: policy profiles)
 * @param policyIds policy definitions used by the offers after the run (phase 6)
 * @param contractDefinitionIds contract definitions of the offers after the run (phase 6)
 */
public record ProvisioningState(
        List<String> assets,
        List<String> partners,
        boolean ok,
        Instant lastRun,
        String error,
        java.util.Map<String, String> usagePolicies,
        List<String> policyIds,
        List<String> contractDefinitionIds) {

    public static final ProvisioningState INITIAL = new ProvisioningState(List.of(), List.of(), false, null, null);

    public ProvisioningState {
        assets = assets == null ? List.of() : List.copyOf(assets);
        partners = partners == null ? List.of() : List.copyOf(partners);
        usagePolicies = usagePolicies == null ? java.util.Map.of() : java.util.Map.copyOf(usagePolicies);
        policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
        contractDefinitionIds = contractDefinitionIds == null ? List.of() : List.copyOf(contractDefinitionIds);
    }

    public ProvisioningState(
            List<String> assets,
            List<String> partners,
            boolean ok,
            Instant lastRun,
            String error,
            java.util.Map<String, String> usagePolicies) {
        this(assets, partners, ok, lastRun, error, usagePolicies, List.of(), List.of());
    }

    public ProvisioningState(List<String> assets, List<String> partners, boolean ok, Instant lastRun, String error) {
        this(assets, partners, ok, lastRun, error, java.util.Map.of());
    }
}
