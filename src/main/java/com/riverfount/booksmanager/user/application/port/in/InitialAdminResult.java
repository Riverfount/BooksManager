package com.riverfount.booksmanager.user.application.port.in;

/** What happened when the initial administrator was requested (RF08). */
public enum InitialAdminResult {
    /** There was no user and the administrator was created. */
    CREATED,
    /** There is already at least one user: nothing was created or changed. */
    ALREADY_HAS_USERS,
    /** There is no user, but the login or the password was not informed: nothing was created. */
    NOT_CONFIGURED
}
