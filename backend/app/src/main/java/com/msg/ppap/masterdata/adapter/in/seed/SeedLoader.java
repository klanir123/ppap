package com.msg.ppap.masterdata.adapter.in.seed;

import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.MasterdataSeeded;
import com.msg.ppap.masterdata.adapter.in.rest.CatalogExport;
import com.msg.ppap.masterdata.api.SeedContributor;
import com.msg.ppap.masterdata.application.CatalogTransferService;
import com.msg.ppap.masterdata.application.imports.ImportEntity;
import com.msg.ppap.masterdata.application.imports.ImportFailedException;
import com.msg.ppap.masterdata.application.imports.ImportResult;
import com.msg.ppap.masterdata.application.imports.ImportService;
import com.msg.ppap.masterdata.config.SeedProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads seed data on startup through the regular import services (upsert by business key, idempotent; no special
 * code path). Order: catalog → partners → sites → parts → relations → part mappings. Failures are logged as
 * warnings; the application keeps running. All changes are audited with actor {@value ActorProvider#SEED_ACTOR}.
 */
@Component
public class SeedLoader {

    static final String CATALOG_FILE = "catalog.json";

    /** Seed files in load order. */
    static final List<Map.Entry<String, ImportEntity>> FILES = List.of(
            Map.entry("partners.csv", ImportEntity.PARTNERS),
            Map.entry("sites.csv", ImportEntity.SITES),
            Map.entry("parts.csv", ImportEntity.PARTS),
            Map.entry("relations.csv", ImportEntity.RELATIONS),
            Map.entry("part-mappings.csv", ImportEntity.PART_MAPPINGS));

    private static final Logger LOG = LoggerFactory.getLogger(SeedLoader.class);

    private final SeedProperties properties;
    private final ImportService imports;
    private final CatalogTransferService catalog;
    private final ActorProvider actors;
    private final TenantContext tenant;
    private final JsonMapper jsonMapper;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ObjectProvider<SeedContributor> contributors;

    public SeedLoader(
            SeedProperties properties,
            ImportService imports,
            CatalogTransferService catalog,
            ActorProvider actors,
            TenantContext tenant,
            JsonMapper jsonMapper,
            ApplicationEventPublisher publisher,
            TransactionTemplate transactions,
            Clock clock,
            ObjectProvider<SeedContributor> contributors) {
        this.properties = properties;
        this.imports = imports;
        this.catalog = catalog;
        this.actors = actors;
        this.tenant = tenant;
        this.jsonMapper = jsonMapper;
        this.publisher = publisher;
        this.transactions = transactions;
        this.clock = clock;
        this.contributors = contributors;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        if (!properties.enabled()) {
            return;
        }
        if (properties.path() == null && properties.commonPath() == null) {
            LOG.warn("Seed loading enabled but neither PPAP_SEED_PATH nor PPAP_SEED_COMMON_PATH is set");
            return;
        }
        try {
            load(properties.commonPath(), properties.path(), properties.documentsPath());
        } catch (RuntimeException ex) {
            LOG.warn("Seed loading failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Loads the seed files from the given folders (missing folders or files are skipped).
     *
     * @return result per file ({@code created/updated/unchanged/failed} or {@code error})
     */
    public Map<String, Object> load(Path commonPath, Path participantPath) {
        return load(commonPath, participantPath, properties.documentsPath());
    }

    /**
     * Loads master data seed files, then the seed files of downstream modules ({@link SeedContributor}, e.g.
     * approval cases and form templates).
     *
     * @return result per file ({@code created/updated/unchanged/failed} or {@code error})
     */
    public Map<String, Object> load(Path commonPath, Path participantPath, Path documentsPath) {
        Map<String, Object> summary = new LinkedHashMap<>();
        actors.runAs(ActorProvider.SEED_ACTOR, () -> {
            if (commonPath != null) {
                summary.put(CATALOG_FILE, loadCatalog(commonPath.resolve(CATALOG_FILE)));
            }
            if (participantPath != null) {
                for (var file : FILES) {
                    summary.put(file.getKey(), loadFile(participantPath.resolve(file.getKey()), file.getValue()));
                }
            }
            var paths = new SeedContributor.SeedPaths(commonPath, participantPath, documentsPath);
            contributors
                    .orderedStream()
                    .sorted(Comparator.comparingInt(SeedContributor::order))
                    .forEach(contributor -> {
                        try {
                            summary.putAll(contributor.load(paths));
                        } catch (RuntimeException ex) {
                            LOG.warn(
                                    "Seed contributor {} failed: {}",
                                    contributor.getClass().getSimpleName(),
                                    ex.getMessage(),
                                    ex);
                            summary.put(
                                    contributor.getClass().getSimpleName(),
                                    Map.of("error", String.valueOf(ex.getMessage())));
                        }
                    });
            transactions.executeWithoutResult(status -> publisher.publishEvent(new MasterdataSeeded(
                    tenant.tenantId(),
                    clock.instant(),
                    ActorProvider.SEED_ACTOR,
                    "SEEDED",
                    "SEED",
                    tenant.tenantId(),
                    summary)));
        });
        LOG.info("Seed data loaded for {}: {}", tenant.tenantId(), summary);
        return summary;
    }

    private Object loadCatalog(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.info("Seed file {} not found, skipped", file);
            return "missing";
        }
        try {
            CatalogExport export = jsonMapper.readValue(Files.readString(file), CatalogExport.class);
            return counts(catalog.importCatalog(export.toCommand(), false));
        } catch (ImportFailedException ex) {
            LOG.warn("Seed file {} rejected: {}", file, ex.result().failures());
            return counts(ex.result());
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Seed file {} could not be loaded: {}", file, ex.getMessage());
            return Map.of("error", String.valueOf(ex.getMessage()));
        }
    }

    private Object loadFile(Path file, ImportEntity entity) {
        if (!Files.isRegularFile(file)) {
            LOG.info("Seed file {} not found, skipped", file);
            return "missing";
        }
        try {
            byte[] content = Files.readAllBytes(file);
            return counts(imports.execute(entity, file.getFileName().toString(), content, null, false));
        } catch (ImportFailedException ex) {
            LOG.warn("Seed file {} rejected: {}", file, ex.result().failures());
            return counts(ex.result());
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Seed file {} could not be loaded: {}", file, ex.getMessage());
            return Map.of("error", String.valueOf(ex.getMessage()));
        }
    }

    private static Map<String, Object> counts(ImportResult result) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("created", result.created());
        counts.put("updated", result.updated());
        counts.put("unchanged", result.unchanged());
        counts.put("failed", result.failed());
        return counts;
    }
}
