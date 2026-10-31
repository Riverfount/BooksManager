package com.riverfount.booksmanager.user.application.port.in;

/** What the recovery of an administrator's access did at startup. */
public enum AdminPasswordResetResult {
    /** The password was replaced, the login block cleared and the refresh tokens revoked. */
    RESET,
    /** The login or the new password was not given, so there was nothing to do. */
    NOT_CONFIGURED,
    /** No user has that login; nothing changed. */
    NOT_FOUND,
    /** The user is an operator or a reader: only an administrator is recovered this way; nothing changed. */
    NOT_AN_ADMINISTRATOR,
    /** The administrator is inactive, and a user is never reactivated by the application; nothing changed. */
    INACTIVE,
    /** The stored password already is the new one (a restart with the variable still set); nothing changed. */
    ALREADY_UP_TO_DATE
}
