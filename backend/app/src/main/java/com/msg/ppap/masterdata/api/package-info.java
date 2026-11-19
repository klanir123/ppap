/**
 * Public API of the Master Data module for other modules: read-only queries (parts, partners, relations, part
 * mappings, catalogue) and two extension points implemented by downstream modules – {@link
 * com.msg.ppap.masterdata.api.MasterdataUsage} (reference checks before physical deletion) and {@link
 * com.msg.ppap.masterdata.api.SeedContributor} (seed data that depends on master data).
 *
 * <p>Dependency direction: downstream modules (e.g. {@code approval}) depend on this package; master data never
 * depends on them. Implementations of the extension points are discovered as Spring beans at runtime.
 */
@NamedInterface("api")
package com.msg.ppap.masterdata.api;

import org.springframework.modulith.NamedInterface;
