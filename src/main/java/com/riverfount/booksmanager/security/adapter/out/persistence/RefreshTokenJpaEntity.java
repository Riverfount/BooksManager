package com.riverfount.booksmanager.security.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Refresh token JPA entity, mapped to the table created by migration
 * V4__create_refresh_token.sql. Separate from the domain; the conversion is
 * done by RefreshTokenPersistenceAdapter.
 */
@Entity
@Table(name = "refresh_token")
class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private Long replacedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenJpaEntity() {
        // required by JPA
    }

    RefreshTokenJpaEntity(Long id, Long userId, String tokenHash, Instant expiresAt, Instant revokedAt,
            Long replacedBy, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getUserId() {
        return userId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    Long getReplacedBy() {
        return replacedBy;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
