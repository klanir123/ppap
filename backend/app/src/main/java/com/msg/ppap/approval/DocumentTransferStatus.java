package com.msg.ppap.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Extension point: transfer state of partner documents referenced by a case (docs/api/pull-api.md, „REST-Ergänzungen“)
 * – evidence of received revisions (customer) and attachments of received requests (supplier). Implemented by the
 * {@code exchange} module; approval only knows this interface (no dependency cycle).
 */
public interface DocumentTransferStatus {

    /** Transfers of the case by SHA-256; documents without transfer are missing in the result. */
    Map<String, Transfer> transfers(UUID caseId);

    /**
     * Transfer of one document.
     *
     * @param status {@code PENDING}, {@code FETCHED}, {@code FAILED} or {@code NOT_AVAILABLE}
     * @param documentId local document once fetched and verified, otherwise {@code null}
     * @param hashVerified whether the fetched content matched the announced SHA-256
     * @param errorCode error code of a failed transfer (e.g. {@code DOCUMENT_HASH_MISMATCH}), otherwise {@code null}
     * @param error last error message, if any
     */
    record Transfer(
            String status, UUID documentId, boolean hashVerified, String errorCode, String error, Instant updatedAt) {}
}
