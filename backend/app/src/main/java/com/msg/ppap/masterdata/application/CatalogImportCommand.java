package com.msg.ppap.masterdata.application;

import java.util.List;

/** Catalog to import (same format as the export and {@code seed/common/catalog.json}). */
public record CatalogImportCommand(
        List<CatalogElementCommand> elements,
        List<SubmissionLevelCommand> levels,
        List<SubmissionReasonCommand> reasons) {

    public CatalogImportCommand {
        elements = elements == null ? List.of() : elements;
        levels = levels == null ? List.of() : levels;
        reasons = reasons == null ? List.of() : reasons;
    }
}
