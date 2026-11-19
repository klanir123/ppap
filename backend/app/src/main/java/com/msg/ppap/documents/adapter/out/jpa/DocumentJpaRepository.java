package com.msg.ppap.documents.adapter.out.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {

    Optional<DocumentEntity> findByTenantIdAndId(String tenantId, UUID id);

    List<DocumentEntity> findByTenantIdAndIdIn(String tenantId, Collection<UUID> ids);
}
