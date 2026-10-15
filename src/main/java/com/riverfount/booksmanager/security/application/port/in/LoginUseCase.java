package com.riverfount.booksmanager.security.application.port.in;

/**
 * Logs a user in (RF08, HU05): checks the credentials through the user
 * module and issues an access token (JWT) and a refresh token (opaque).
 */
public interface LoginUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.InvalidCredentialsException
     *         for a wrong login, a wrong password or an inactive user, always with the same message
     */
    TokenPair login(LoginCommand command);
}
