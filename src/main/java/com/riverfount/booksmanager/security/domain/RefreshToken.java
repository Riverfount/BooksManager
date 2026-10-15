package com.riverfount.booksmanager.security.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;

/**
 * A refresh token (RF08): opaque, so only its hash is kept here, never the
 * token itself. It belongs to a user (referenced by id), lives for a period
 * from the moment it is issued and can be revoked, in which case it may point
 * to the token that replaced it (rotation). The dates come by parameter,
 * never from the system clock.
 */
public class RefreshToken {

    private final Long id;
    private final Long userId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private Long replacedBy;

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

    /** Whether the token stops being valid at or before the given instant. */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Revokes the token (logout, or all the tokens of a user). Revoking a token
     * that is already revoked changes nothing and keeps the first date.
     *
     * @return whether the token changed and has to be saved
     */
    public boolean revoke(Instant now) {
        if (now == null) {
            throw new BusinessRuleException("a data de revogação do refresh token é obrigatória");
        }
        if (revokedAt != null) {
            return false;
        }
        this.revokedAt = now;
        return true;
    }

    /**
     * Rotation: the token is used once, so it is revoked and points to the one
     * that replaces it. A token already revoked cannot be rotated: using it
     * again is a reuse, which the application answers by revoking the user's tokens.
     */
    public void rotate(Instant now, Long replacementId) {
        if (replacementId == null || replacementId.equals(id)) {
            throw new BusinessRuleException("o refresh token precisa ser substituído por outro token");
        }
        if (revokedAt != null) {
            throw new BusinessRuleException("o refresh token já foi revogado e não pode ser girado");
        }
        revoke(now);
        this.replacedBy = replacementId;
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
