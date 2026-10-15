package com.riverfount.booksmanager.security.application.service;

import com.riverfount.booksmanager.security.application.port.out.RefreshTokenRepository;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of RefreshTokenRepository, used only in the
 * application services' unit tests (no Spring, no database). Like the
 * database, it refuses a repeated hash, and like the JPA adapter it never
 * hands out the stored instance. A test fake, used from a single thread.
 */
class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<Long, RefreshToken> tokens = new HashMap<>();
    private long nextId = 1;

    private static RefreshToken copyWithId(RefreshToken token, Long id) {
        return RefreshToken.reconstitute(id, token.getUserId(), token.getTokenHash(), token.getCreatedAt(),
                token.getExpiresAt(), token.getRevokedAt(), token.getReplacedBy());
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        var duplicate = tokens.values().stream()
                .anyMatch(other -> other.getTokenHash().equals(token.getTokenHash())
                        && !other.getId().equals(token.getId()));
        if (duplicate) {
            throw new IllegalStateException("repeated token hash");
        }
        var id = token.getId() != null ? token.getId() : nextId++;
        tokens.put(id, copyWithId(token, id));
        return copyWithId(token, id);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return tokens.values().stream().filter(token -> token.getTokenHash().equals(tokenHash)).findFirst()
                .map(token -> copyWithId(token, token.getId()));
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return findByTokenHash(tokenHash);
    }

    @Override
    public int revokeAllActiveOfUser(Long userId, java.time.Instant now) {
        var revoked = 0;
        for (var token : new java.util.ArrayList<>(tokens.values())) {
            if (token.getUserId().equals(userId) && !token.isRevoked()) {
                token.revoke(now);
                tokens.put(token.getId(), copyWithId(token, token.getId()));
                revoked++;
            }
        }
        return revoked;
    }

    int count() {
        return tokens.size();
    }
}
