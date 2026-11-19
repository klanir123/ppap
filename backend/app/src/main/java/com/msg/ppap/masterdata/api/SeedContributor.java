package com.msg.ppap.masterdata.api;

import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Extension point of the seed loader: downstream modules load their seed files after the master data (same actor
 * {@code system:seed}, same summary and audit event). Contributors run in ascending {@link #order()}.
 */
public interface SeedContributor {

    /** Execution order (ascending). */
    default int order() {
        return 0;
    }

    /**
     * Loads seed files (missing folders or files are skipped); failures are reported in the summary, never thrown.
     *
     * @return result per seed file name
     */
    Map<String, Object> load(SeedPaths paths);

    /**
     * Seed folders.
     *
     * @param common folder shared by all participants ({@code PPAP_SEED_COMMON_PATH})
     * @param participant participant folder ({@code PPAP_SEED_PATH})
     * @param documents folder with example documents ({@code PPAP_SEED_DOCUMENTS_PATH})
     */
    record SeedPaths(
            @Nullable Path common,
            @Nullable Path participant,
            @Nullable Path documents) {}
}
