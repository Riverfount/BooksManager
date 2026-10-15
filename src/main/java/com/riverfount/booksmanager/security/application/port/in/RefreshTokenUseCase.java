package com.riverfount.booksmanager.security.application.port.in;

/**
 * Renews the access token with a refresh token (RF08). The refresh token is
 * used once: it is revoked and replaced by a new one (rotation). Using a token
 * that was already revoked means it may have leaked, so every token of that
 * user is revoked.
 */
public interface RefreshTokenUseCase {

    /**
     * @throws InvalidRefreshTokenException when the token is unknown, expired,
     *         revoked or of a user who is no longer active
     */
    TokenPair refresh(RefreshCommand command);
}
