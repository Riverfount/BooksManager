package com.riverfount.booksmanager.security.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;

/**
 * A refresh token (RF08): opaque, so only its hash is kept here, never the
 * token itself. It belongs to a user (referenced by id), lives for a period
 * from the moment it is issued and can be revoked, in which case it may point
 * to the token that replaced it (rotation, issue #104). The dates come by
 * parameter, never from the system clock.
 */
public class RefreshToken {

    private final Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final Long replacedBy;

    private RefreshToken(Long id, Long userId, String tokenHash, Instant createdAt, Instant expiresAt,
            Instant revokedAt, Long replacedBy) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
    }

    public static RefreshToken issue(Long userId, String tokenHash, Instant issuedAt, Duration lifetime) {
        if (userId == null) {
            throw new BusinessRuleException("o usuário do refresh token é obrigatório");
        }
        if (!TokenHash.isWellFormed(tokenHash)) {
            throw new BusinessRuleException("o refresh token deve ser guardado como hash SHA-256");
        }
        if (issuedAt == null) {
            throw new BusinessRuleException("a data de emissão do refresh token é obrigatória");
        }
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new BusinessRuleException("a validade do refresh token deve ser positiva");
        }
        return new RefreshToken(null, userId, tokenHash, issuedAt, issuedAt.plus(lifetime), null, null);
    }

    public static RefreshToken reconstitute(Long id, Long userId, String tokenHash, Instant createdAt,
            Instant expiresAt, Instant revokedAt, Long replacedBy) {
        return new RefreshToken(id, userId, tokenHash, createdAt, expiresAt, revokedAt, replacedBy);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getReplacedBy() {
        return replacedBy;
    }

    /** Without the hash, so the string form is safe to log. */
    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", userId=" + userId + ", expiresAt=" + expiresAt
                + ", revoked=" + (revokedAt != null) + "]";
    }
}
