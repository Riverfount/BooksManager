package com.riverfount.booksmanager.security.application.port.out;

import com.riverfount.booksmanager.security.domain.RefreshToken;
import java.util.Optional;

/**
 * Outbound port for refresh token persistence, in the domain's language.
 * Tokens are only ever found by their hash.
 */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
