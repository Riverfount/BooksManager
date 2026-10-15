package com.riverfount.booksmanager.security.application.port.out;

import com.riverfount.booksmanager.security.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for refresh token persistence, in the domain's language.
 * Tokens are only ever found by their hash.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Like {@link #findByTokenHash}, but the row is locked until the end of the
     * current transaction, so two requests using the same token are handled one
     * after the other: the second then sees it already revoked (otherwise both
     * would rotate it and the chain of tokens would fork).
     */
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    /**
     * Revokes every token of the user that is not revoked yet (reuse detected,
     * or the user was inactivated), keeping the date of the earlier revocations.
     *
     * @return how many tokens were revoked
     */
    int revokeAllActiveOfUser(Long userId, Instant now);
}
