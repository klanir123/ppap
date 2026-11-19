package com.msg.ppap.documents.api;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-local document store used by other modules (e.g. approval attachments, Excel import, seed). */
public interface Documents {

    /**
     * Uploads a document: size limit, content type detection by magic bytes against the whitelist, malware scan,
     * SHA-256 and content-addressed storage.
     *
     * @throws com.msg.ppap.platform.domain.PpapException {@code FILE_TOO_LARGE}, {@code UNSUPPORTED_FILE_TYPE},
     *     {@code EMPTY_FILE}, {@code MALWARE_DETECTED}
     */
    DocumentInfo upload(String fileName, InputStream content);

    /** Metadata of a document of the current tenant. */
    Optional<DocumentInfo> find(UUID id);

    /**
     * Metadata of a document of the current tenant.
     *
     * @throws com.msg.ppap.platform.domain.NotFoundException if unknown
     */
    DocumentInfo get(UUID id);

    /** Metadata of the given documents (unknown ids are skipped). */
    List<DocumentInfo> findAll(Collection<UUID> ids);
}
