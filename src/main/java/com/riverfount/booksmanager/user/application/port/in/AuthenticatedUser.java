package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Role;

/**
 * Who was authenticated: only what the security module needs to issue the
 * tokens (the id, the profile and, for the access of a reader, the reader). It carries no password, hash or login.
 */
public record AuthenticatedUser(Long id, Role role, Long readerId) {

    /** A user of the system (administrator or operator), who is not tied to a reader. */
    public AuthenticatedUser(Long id, Role role) {
        this(id, role, null);
    }
}
