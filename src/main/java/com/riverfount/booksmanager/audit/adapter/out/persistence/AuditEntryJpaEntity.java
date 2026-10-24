package com.riverfount.booksmanager.audit.adapter.out.persistence;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Audit entry JPA entity, mapped to the table created by migration V12__create_audit_log.sql. Immutable: Hibernate
 * never issues an UPDATE for it, and the database refuses one as well. Separate from the domain; the conversion is
 * done by AuditEntryPersistenceAdapter.
 */
@Entity
@Immutable
@Table(name = "audit_log")
class AuditEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_login", length = 50)
    private String actorLogin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private AuditTarget targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 500)
    private String summary;

    protected AuditEntryJpaEntity() {
        // required by JPA
    }

    AuditEntryJpaEntity(Instant occurredAt, Long actorId, String actorLogin, AuditAction action,
            AuditTarget targetType, Long targetId, String summary) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorLogin = actorLogin;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
    }

    Long getId() {
        return id;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    Long getActorId() {
        return actorId;
    }

    String getActorLogin() {
        return actorLogin;
    }

    AuditAction getAction() {
        return action;
    }

    AuditTarget getTargetType() {
        return targetType;
    }

    Long getTargetId() {
        return targetId;
    }

    String getSummary() {
        return summary;
    }
}
