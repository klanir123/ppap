package com.msg.ppap.masterdata.application.imports;

import java.util.List;

/**
 * Result of an import execution.
 *
 * @param entity imported entity ({@code parts}, …, {@code catalog})
 * @param created created objects
 * @param updated updated objects
 * @param unchanged unchanged objects
 * @param failed failed rows
 * @param failures failure details
 */
public record ImportResult(
        String entity, int created, int updated, int unchanged, int failed, List<ImportFailure> failures) {

    public ImportResult {
        failures = List.copyOf(failures);
    }
}
