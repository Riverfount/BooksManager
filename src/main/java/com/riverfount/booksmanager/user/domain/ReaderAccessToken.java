package com.riverfount.booksmanager.user.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A single-use link token a reader receives by e-mail (Phase 6): the invitation that grants the access, or the
 * request to choose a new password. The token is opaque and random, so only its SHA-256 hash is kept, never the
 * token itself. It belongs to a reader (by id), lives for a period from the moment it is issued and is used once.
 * The dates come by parameter, never from the system clock.
 */
public class ReaderAccessToken {

    /** What the token allows. */
    public enum Purpose {
        INVITATION,
        PASSWORD_RESET
    }

    private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

    private final Long id;
    private final Purpose purpose;
    private final Long readerId;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private ReaderAccessToken(Long id, Purpose purpose, Long readerId, String tokenHash, Instant createdAt,
            Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.purpose = purpose;
        this.readerId = readerId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    public static ReaderAccessToken issue(Purpose purpose, Long readerId, String tokenHash, Instant issuedAt,
            Duration validity) {
        if (purpose == null) {
            throw new BusinessRuleException("a finalidade do token de acesso é obrigatória");
        }
        if (readerId == null) {
            throw new BusinessRuleException("o leitor do token de acesso é obrigatório");
        }
        if (tokenHash == null || !HASH.matcher(tokenHash).matches()) {
            throw new BusinessRuleException("o token de acesso deve ser guardado como hash SHA-256");
        }
        if (issuedAt == null) {
            throw new BusinessRuleException("a data de emissão do token de acesso é obrigatória");
        }
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new BusinessRuleException("a validade do token de acesso deve ser positiva");
        }
        return new ReaderAccessToken(null, purpose, readerId, tokenHash, issuedAt, issuedAt.plus(validity), null);
    }

    public static ReaderAccessToken reconstitute(Long id, Purpose purpose, Long readerId, String tokenHash,
            Instant createdAt, Instant expiresAt, Instant usedAt) {
        return new ReaderAccessToken(id, purpose, readerId, tokenHash, createdAt, expiresAt, usedAt);
    }

    /** SHA-256 in lowercase hexadecimal: the form in which a token is kept and looked up. */
    public static String hashOf(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("o token é obrigatório");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every Java platform
            throw new IllegalStateException(e);
        }
    }

    /** Whether the token can still be used: not used yet and not expired (it stops being valid at {@code expiresAt}). */
    public boolean canBeUsed(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Marks the token as used. Using it again changes nothing and keeps the first date.
     *
     * @return whether the token changed and has to be saved
     */
    public boolean use(Instant now) {
        if (now == null) {
            throw new BusinessRuleException("a data de uso do token de acesso é obrigatória");
        }
        if (usedAt != null) {
            return false;
        }
        this.usedAt = now;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public Long getReaderId() {
        return readerId;
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

    public Instant getUsedAt() {
        return usedAt;
    }
}
