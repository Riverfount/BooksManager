package com.riverfount.booksmanager.security.application.port.in;

/**
 * Ends a session by revoking its refresh token (RF08). Idempotent: an unknown,
 * missing or already revoked token is not an error, and says nothing about
 * whether the token ever existed.
 */
public interface LogoutUseCase {

    void logout(LogoutCommand command);
}
