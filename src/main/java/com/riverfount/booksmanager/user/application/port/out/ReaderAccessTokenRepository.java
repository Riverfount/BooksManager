package com.riverfount.booksmanager.user.application.port.out;

import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import java.time.Instant;
import java.util.Optional;

/**
 * Outbound port for the reader access tokens (the invitation and the password reset link), in the domain's
 * language. A token is only ever looked up by its hash.
 */
public interface ReaderAccessTokenRepository {

    ReaderAccessToken save(ReaderAccessToken token);

    /**
     * Finds by hash, locking the row until the end of the current transaction, so two requests with the same token
     * are handled one after the other (a token is used once). A null hash finds nothing.
     */
    Optional<ReaderAccessToken> findByTokenHashForUpdate(String tokenHash);

    /**
     * Marks as used, at the given instant, every token of the reader and the purpose that is still unused, so that
     * a new link replaces the previous ones.
     *
     * @return how many tokens were invalidated
     */
    int invalidatePending(Long readerId, ReaderAccessToken.Purpose purpose, Instant now);
}
