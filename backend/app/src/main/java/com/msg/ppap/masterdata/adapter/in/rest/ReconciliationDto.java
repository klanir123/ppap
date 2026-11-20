package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.api.OfferReconciler;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;

/** Effect of a write on the offers of the own EDC (policy profiles, relations; phase 6). */
public record ReconciliationDto(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"APPLIED", "PENDING", "FAILED"})
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Policy definitions used by the offers")
        List<String> policyIds,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Contract definitions of the offers")
        List<String> contractDefinitionIds,

        String error) {

    /** Runs the reconciliation if the exchange module is present, else {@code null}. */
    static ReconciliationDto run(ObjectProvider<OfferReconciler> reconciler) {
        OfferReconciler r = reconciler.getIfAvailable();
        if (r == null) {
            return null;
        }
        OfferReconciler.Result result = r.reconcileNow();
        return new ReconciliationDto(
                result.status(), result.policyIds(), result.contractDefinitionIds(), result.error());
    }
}
