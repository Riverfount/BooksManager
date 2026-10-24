package com.riverfount.booksmanager.audit.adapter.out.persistence;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEntryJpaRepository extends JpaRepository<AuditEntryJpaEntity, Long> {

    /** The listing (RF27). A null parameter means "no filter on that field"; the period is {@code [from, toExclusive)}. */
    @Query("""
            select e from AuditEntryJpaEntity e
            where (:actorId is null or e.actorId = :actorId)
              and (:action is null or e.action = :action)
              and (:target is null or e.targetType = :target)
              and (:targetId is null or e.targetId = :targetId)
              and (cast(:from as instant) is null or e.occurredAt >= :from)
              and (cast(:toExclusive as instant) is null or e.occurredAt < :toExclusive)
            """)
    Page<AuditEntryJpaEntity> search(@Param("actorId") Long actorId, @Param("action") AuditAction action,
            @Param("target") AuditTarget target, @Param("targetId") Long targetId, @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive, Pageable pageable);
}
