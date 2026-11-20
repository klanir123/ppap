package com.msg.ppap.masterdata.api;

import java.util.List;

/**
 * Extension point: applies master data (policy profiles, relations) to the offers of the own dataspace connector right
 * after a change, so that write responses can report the effect (docs/api/cascade-transparency-api.md §3).
 * Implemented by the {@code exchange} module; master data only knows this interface (no dependency cycle).
 */
public interface OfferReconciler {

    /**
     * Runs the reconciliation now; waits at most the configured timeout, otherwise it finishes asynchronously.
     *
     * @return {@code APPLIED}, {@code FAILED} or {@code PENDING} (still running)
     */
    Result reconcileNow();

    /**
     * Outcome of a reconciliation triggered by a write.
     *
     * @param status {@code APPLIED}, {@code PENDING} or {@code FAILED}
     * @param policyIds policy definitions used by the offers (empty unless {@code APPLIED})
     * @param contractDefinitionIds contract definitions of the offers (empty unless {@code APPLIED})
     * @param error failure, if any
     */
    record Result(String status, List<String> policyIds, List<String> contractDefinitionIds, String error) {

        public Result {
            policyIds = policyIds == null ? List.of() : List.copyOf(policyIds);
            contractDefinitionIds = contractDefinitionIds == null ? List.of() : List.copyOf(contractDefinitionIds);
        }
    }
}
