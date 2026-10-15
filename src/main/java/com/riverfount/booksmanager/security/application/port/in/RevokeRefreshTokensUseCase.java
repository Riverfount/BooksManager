package com.riverfount.booksmanager.security.application.port.in;

/**
 * Public port of the security module for the user module (RF08, RF09): revokes
 * every refresh token of a user, which the user management calls when it
 * inactivates a user (issue #109), so the user cannot renew any session.
 */
public interface RevokeRefreshTokensUseCase {

    /** @return how many tokens were revoked */
    int revokeAllOf(Long userId);
}
