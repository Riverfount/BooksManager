package com.riverfount.booksmanager.user.domain;

/**
 * Profile of a system user (RF08, RF09): the administrator manages users
 * and the library configuration; the operator runs the day-to-day work. The reader
 * has a limited access to the system (Phase 6): it is only ever tied to one reader and
 * is granted by invitation, never created or assigned by hand.
 */
public enum Role {
    ADMIN,
    OPERATOR,
    READER
}
