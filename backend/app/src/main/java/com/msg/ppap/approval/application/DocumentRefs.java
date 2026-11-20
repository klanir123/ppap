package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalErrors;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.documents.api.DocumentInfo;
import com.msg.ppap.documents.api.Documents;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves tenant-local documents into references with a metadata snapshot. */
@Component
public class DocumentRefs {

    private final Documents documents;

    public DocumentRefs(Documents documents) {
        this.documents = documents;
    }

    /** Problem code: a document received from a partner must not be passed on (UC-09, AK-09-2). */
    public static final String RECEIVED_DOCUMENT_NOT_SHAREABLE = "SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE";

    /**
     * Resolves a document that will be shared with a partner (evidence of an own submission, request attachment).
     * Provenance, not content, decides: a document <em>record</em> received from a partner (stored by the document
     * transfer with the actor {@code partner:<BPNL>}) is never passed on; an own upload is allowed even if its bytes
     * equal a received document (content-addressed storage shares the object, not the record).
     *
     * @throws com.msg.ppap.platform.domain.PpapException {@code 422 SUB_SUPPLIER_DOCUMENT_NOT_SHAREABLE}
     */
    public DocumentRef resolveShareable(UUID documentId) {
        DocumentRef ref = resolve(documentId);
        if (received(ref.uploadedBy())) {
            throw ApprovalErrors.unprocessable(
                    RECEIVED_DOCUMENT_NOT_SHAREABLE,
                    "Document " + ref.fileName() + " was received from a partner and is not passed on");
        }
        return ref;
    }

    /** Whether a document record with this uploader was received from a partner. */
    public static boolean received(String uploadedBy) {
        return uploadedBy != null && uploadedBy.startsWith(Actor.PARTNER_PREFIX);
    }

    /** Whether the document record exists and is an own upload (not received from a partner). */
    public boolean ownUpload(UUID documentId) {
        return documents.find(documentId).map(d -> !received(d.uploadedBy())).orElse(false);
    }

    /** @throws com.msg.ppap.platform.domain.NotFoundException if the document does not exist */
    public DocumentRef resolve(UUID documentId) {
        return of(documents.get(documentId));
    }

    public static DocumentRef of(DocumentInfo info) {
        return new DocumentRef(
                info.id(),
                info.fileName(),
                info.mediaType(),
                info.size(),
                info.sha256(),
                info.uploadedAt(),
                info.uploadedBy());
    }
}
