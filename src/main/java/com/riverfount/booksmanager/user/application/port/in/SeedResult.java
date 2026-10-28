package com.riverfount.booksmanager.user.application.port.in;

/** What happened when a startup account (operator or reader) was requested. */
public enum SeedResult {
    /** The account did not exist and was created. */
    CREATED,
    /** A user with this login already exists: nothing was created or changed. */
    ALREADY_EXISTS,
    /** Something required was not informed: nothing was created. */
    NOT_CONFIGURED
}
