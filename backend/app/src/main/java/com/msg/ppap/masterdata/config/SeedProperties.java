package com.msg.ppap.masterdata.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Seed data loaded on startup.
 *
 * @param enabled load seed data on startup ({@code PPAP_SEED_ENABLED})
 * @param path participant folder with {@code partners.csv}, {@code sites.csv}, {@code parts.csv},
 *     {@code relations.csv}, {@code part-mappings.csv} ({@code PPAP_SEED_PATH})
 * @param commonPath folder with {@code catalog.json} shared by all participants ({@code PPAP_SEED_COMMON_PATH})
 */
@ConfigurationProperties("ppap.seed")
public record SeedProperties(boolean enabled, Path path, Path commonPath) {}
