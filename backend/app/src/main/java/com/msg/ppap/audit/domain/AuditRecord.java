package com.msg.ppap.audit.domain;

import com.msg.ppap.semantics.api.CanonicalJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, hash-chained audit log entry. The chain is kept per (tenant, object type, object id): {@code hash} is
 * the hex SHA-256 over the RFC 8785 canonical JSON of all fields except {@code id} and {@code hash}, including
 * {@code prevHash} (the hash of the previous entry of the same object, {@code null} for the first).
 *
 * @param id technical id
 * @param tenantId tenant BPNL
 * @param occurredAt time of the change (microsecond precision, as stored)
 * @param actor acting user, client or technical actor
 * @param action action
 * @param objectType object type
 * @param objectId object id
 * @param details event details
 * @param prevHash hash of the previous entry in the chain
 * @param hash hash of this entry
 */
public record AuditRecord(
        UUID id,
        String tenantId,
        Instant occurredAt,
        String actor,
        String action,
        String objectType,
        String objectId,
        Map<String, Object> details,
        String prevHash,
        String hash) {

    public AuditRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(objectType, "objectType");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(hash, "hash");
        details = details == null ? Map.of() : details;
    }

    /** Creates the next entry of a chain. */
    public static AuditRecord append(
            UUID id,
            String tenantId,
            Instant occurredAt,
            String actor,
            String action,
            String objectType,
            String objectId,
            Map<String, Object> details,
            String prevHash) {
        Instant truncated = occurredAt.truncatedTo(ChronoUnit.MICROS);
        Map<String, Object> safeDetails = details == null ? Map.of() : details;
        String hash = computeHash(tenantId, truncated, actor, action, objectType, objectId, safeDetails, prevHash);
        return new AuditRecord(
                id, tenantId, truncated, actor, action, objectType, objectId, safeDetails, prevHash, hash);
    }

    /** Whether the stored hash matches the content. */
    public boolean hashValid() {
        return hash.equals(computeHash(tenantId, occurredAt, actor, action, objectType, objectId, details, prevHash));
    }

    /** Canonical JSON over which the hash is computed. */
    public static String canonicalContent(
            String tenantId,
            Instant occurredAt,
            String actor,
            String action,
            String objectType,
            String objectId,
            Map<String, Object> details,
            String prevHash) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("tenantId", tenantId);
        content.put("occurredAt", occurredAt.toString());
        content.put("actor", actor);
        content.put("action", action);
        content.put("objectType", objectType);
        content.put("objectId", objectId);
        content.put("details", details);
        content.put("prevHash", prevHash);
        return CanonicalJson.write(content);
    }

    private static String computeHash(
            String tenantId,
            Instant occurredAt,
            String actor,
            String action,
            String objectType,
            String objectId,
            Map<String, Object> details,
            String prevHash) {
        return sha256(canonicalContent(tenantId, occurredAt, actor, action, objectType, objectId, details, prevHash));
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
