package com.msg.ppap.documents.application.port.out;

import com.msg.ppap.documents.domain.Document;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence of document metadata; scoped to the current tenant. */
public interface DocumentRepository {

    Optional<Document> findById(UUID id);

    List<Document> findAllById(Collection<UUID> ids);

    void insert(Document document);
}
