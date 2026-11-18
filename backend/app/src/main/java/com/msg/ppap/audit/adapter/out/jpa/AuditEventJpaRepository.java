package com.msg.ppap.audit.adapter.out.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    @Query("""
            select e.hash from AuditEventEntity e
            where e.tenantId = :tenant and e.objectType = :type and e.objectId = :id
            order by e.seq desc
            limit 1
            """)
    Optional<String> latestHash(@Param("tenant") String tenant, @Param("type") String type, @Param("id") String id);

    @Query("""
            select e from AuditEventEntity e
            where e.tenantId = :tenant
              and (:type is null or e.objectType = :type)
              and (:id is null or e.objectId = :id)
            order by e.seq desc
            """)
    Page<AuditEventEntity> search(
            @Param("tenant") String tenant, @Param("type") String type, @Param("id") String id, Pageable pageable);

    @Query("""
            select e from AuditEventEntity e
            where e.tenantId = :tenant and e.objectType = :type and e.objectId = :id
            order by e.seq asc
            """)
    List<AuditEventEntity> chain(@Param("tenant") String tenant, @Param("type") String type, @Param("id") String id);
}
