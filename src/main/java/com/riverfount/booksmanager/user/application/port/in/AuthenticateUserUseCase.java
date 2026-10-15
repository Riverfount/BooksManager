package com.riverfount.booksmanager.user.application.port.in;

/**
 * Public port of the user module for the security module (RF08, HU05):
 * checks login and password and says who the user is. This module owns the
 * rules (an inactive user cannot be authenticated) and the password hashing;
 * the security module only issues the tokens afterwards.
 */
public interface AuthenticateUserUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.InvalidCredentialsException
     *         when the login does not exist, the password is wrong or the user
     *         is inactive, always with the same message
     */
    AuthenticatedUser authenticate(AuthenticateUserCommand command);
}
