package com.msg.ppap.approval.application;

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
