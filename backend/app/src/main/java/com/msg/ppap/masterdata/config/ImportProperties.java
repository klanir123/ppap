package com.msg.ppap.masterdata.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Master data import settings. The column synonyms come from {@code masterdata-import-synonyms.yaml} (imported via
 * {@code spring.config.import}) and can be overridden like any other configuration.
 *
 * @param maxFileSize maximum upload size of import files
 * @param maxRows maximum number of data rows per file
 * @param previewRows number of rows returned in full by the preview
 * @param synonyms target field name to accepted column headers (compared case-insensitively without spaces and
 *     special characters)
 */
@ConfigurationProperties("ppap.masterdata.import")
public record ImportProperties(
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("10000") int maxRows,
        @DefaultValue("500") int previewRows,
        @DefaultValue Map<String, List<String>> synonyms) {}
