package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.CatalogImportCommand;
import com.msg.ppap.masterdata.application.CatalogSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Complete catalog: format of {@code GET /catalog/export}, {@code POST /catalog/import} and the seed catalog. */
public record CatalogExport(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CatalogElementRequest> elements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SubmissionLevelRequest> levels,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SubmissionReasonRequest> reasons) {

    static CatalogExport from(CatalogSnapshot snapshot) {
        return new CatalogExport(
                snapshot.elements().stream().map(CatalogElementRequest::from).toList(),
                snapshot.levels().stream().map(SubmissionLevelRequest::from).toList(),
                snapshot.reasons().stream().map(SubmissionReasonRequest::from).toList());
    }

    /** Converts to the application command (also used by the seed loader). */
    public CatalogImportCommand toCommand() {
        return new CatalogImportCommand(
                elements == null
                        ? null
                        : elements.stream()
                                .map(CatalogElementRequest::toCommand)
                                .toList(),
                levels == null
                        ? null
                        : levels.stream().map(SubmissionLevelRequest::toCommand).toList(),
                reasons == null
                        ? null
                        : reasons.stream()
                                .map(SubmissionReasonRequest::toCommand)
                                .toList());
    }
}
