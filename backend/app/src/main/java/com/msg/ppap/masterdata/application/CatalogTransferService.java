package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.application.imports.ImportFailedException;
import com.msg.ppap.masterdata.application.imports.ImportFailure;
import com.msg.ppap.masterdata.application.imports.ImportMessage;
import com.msg.ppap.masterdata.application.imports.ImportResult;
import com.msg.ppap.platform.domain.DuplicateKeyException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export and import of the complete submission catalog (UC-02 A1). The import upserts by code in the order
 * elements → levels → reasons, in one transaction.
 */
@Service
public class CatalogTransferService {

    /** Entity name used in import results. */
    public static final String ENTITY = "catalog";

    private final CatalogElementService elements;
    private final SubmissionLevelService levels;
    private final SubmissionReasonService reasons;

    public CatalogTransferService(
            CatalogElementService elements, SubmissionLevelService levels, SubmissionReasonService reasons) {
        this.elements = elements;
        this.levels = levels;
        this.reasons = reasons;
    }

    @Transactional(readOnly = true)
    public CatalogSnapshot export() {
        return new CatalogSnapshot(elements.list(null), levels.list(null), reasons.list(null));
    }

    /**
     * Imports the catalog. Row numbers in the result count entries across all sections (elements first, then
     * levels, then reasons); message fields are prefixed with the section and index, e.g. {@code levels[2].code}.
     *
     * @throws ImportFailedException if entries fail and {@code skipErrors} is not set (nothing is imported)
     */
    @Transactional
    public ImportResult importCatalog(CatalogImportCommand command, boolean skipErrors) {
        var counter = new Counter();
        var failures = new ArrayList<ImportFailure>();
        process("elements", command.elements(), c -> elements.upsert(c).action(), counter, failures);
        process("levels", command.levels(), c -> levels.upsert(c).action(), counter, failures);
        process("reasons", command.reasons(), c -> reasons.upsert(c).action(), counter, failures);
        if (!failures.isEmpty() && !skipErrors) {
            throw new ImportFailedException(new ImportResult(ENTITY, 0, 0, 0, failures.size(), failures));
        }
        return new ImportResult(ENTITY, counter.created, counter.updated, counter.unchanged, failures.size(), failures);
    }

    private static <C> void process(
            String section,
            List<C> commands,
            Function<C, ChangeAction> upsert,
            Counter counter,
            List<ImportFailure> failures) {
        for (int i = 0; i < commands.size(); i++) {
            counter.row++;
            try {
                switch (upsert.apply(commands.get(i))) {
                    case CREATED -> counter.created++;
                    case UPDATED -> counter.updated++;
                    case UNCHANGED -> counter.unchanged++;
                }
            } catch (PpapException ex) {
                failures.add(new ImportFailure(counter.row, messages(section + "[" + i + "]", ex)));
            }
        }
    }

    private static List<ImportMessage> messages(String prefix, PpapException ex) {
        if (ex instanceof ValidationException validation) {
            return validation.violations().stream()
                    .map(v -> new ImportMessage(prefix + "." + v.field(), v.code(), v.message()))
                    .toList();
        }
        String field = ex instanceof DuplicateKeyException duplicate ? prefix + "." + duplicate.field() : prefix;
        return List.of(new ImportMessage(field, ex.code(), ex.getMessage()));
    }

    private static final class Counter {
        int row;
        int created;
        int updated;
        int unchanged;
    }
}
