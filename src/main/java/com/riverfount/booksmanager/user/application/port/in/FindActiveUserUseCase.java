package com.riverfount.booksmanager.user.application.port.in;

import java.util.Optional;

/**
 * Public port of the user module for the security module (RF08): who a user
 * is right now, if the user still exists and is active. Used when a refresh
 * token is renewed, so that an inactivated user stops getting tokens and a
 * changed profile reaches the new access token. A user blocked by the login
 * lockout is still found: that block only guards the password.
 */
public interface FindActiveUserUseCase {

    Optional<AuthenticatedUser> findActiveUser(Long id);
}
