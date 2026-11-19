package com.msg.ppap.approval.adapter.in.seed;

import com.msg.ppap.approval.application.seed.ApprovalSeedService;
import com.msg.ppap.approval.application.seed.SeedCases;
import com.msg.ppap.approval.domain.FormTemplate;
import com.msg.ppap.masterdata.api.SeedContributor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Seed files of the approval module, loaded after the master data: {@code <common>/form-templates.json} and
 * {@code <participant>/approval-cases.json} (documents from {@code PPAP_SEED_DOCUMENTS_PATH}).
 */
@Component
class ApprovalSeedContributor implements SeedContributor {

    static final String TEMPLATES_FILE = "form-templates.json";
    static final String CASES_FILE = "approval-cases.json";

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalSeedContributor.class);

    private final ApprovalSeedService seed;
    private final JsonMapper jsonMapper;

    ApprovalSeedContributor(ApprovalSeedService seed, JsonMapper jsonMapper) {
        this.seed = seed;
        this.jsonMapper = jsonMapper
                .rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public Map<String, Object> load(SeedPaths paths) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (paths.common() != null) {
            summary.put(TEMPLATES_FILE, templates(paths.common().resolve(TEMPLATES_FILE)));
        }
        if (paths.participant() != null) {
            summary.put(CASES_FILE, cases(paths.participant().resolve(CASES_FILE), paths.documents()));
        }
        return summary;
    }

    private Object templates(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.info("Seed file {} not found, skipped", file);
            return "missing";
        }
        try {
            TemplatesFile parsed = jsonMapper.readValue(file.toFile(), TemplatesFile.class);
            return seed.importTemplates(
                    parsed.templates().stream().map(TemplateJson::toDomain).toList());
        } catch (RuntimeException ex) {
            LOG.warn("Seed file {} could not be loaded: {}", file, ex.getMessage());
            return Map.of("error", String.valueOf(ex.getMessage()));
        }
    }

    private Object cases(Path file, Path documents) {
        if (!Files.isRegularFile(file)) {
            LOG.info("Seed file {} not found, skipped", file);
            return "missing";
        }
        try {
            SeedCases parsed = jsonMapper.readValue(file.toFile(), SeedCases.class);
            return seed.importCases(parsed, documents);
        } catch (RuntimeException ex) {
            LOG.warn("Seed file {} could not be loaded: {}", file, ex.getMessage());
            return Map.of("error", String.valueOf(ex.getMessage()));
        }
    }

    record TemplatesFile(List<TemplateJson> templates) {

        TemplatesFile {
            templates = templates == null ? List.of() : templates;
        }
    }

    record TemplateJson(
            String code,
            String nameDe,
            String nameEn,
            FormTemplate.Marker marker,
            List<FormTemplate.HeaderField> header,
            FormTemplate.ElementTable elementTable,
            LinkedHashMap<String, String> selfAssessmentValues,
            Boolean active) {

        FormTemplate toDomain() {
            return new FormTemplate(
                    code, nameDe, nameEn, marker, header, elementTable, selfAssessmentValues, active == null || active);
        }
    }
}
