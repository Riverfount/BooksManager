package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Role;

/**
 * Who was authenticated: only what the security module needs to issue the
 * tokens (the id and the profile). It carries no password, hash or login.
 */
public record AuthenticatedUser(Long id, Role role) {
}
