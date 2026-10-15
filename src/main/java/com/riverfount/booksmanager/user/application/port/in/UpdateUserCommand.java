package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.Role;

/**
 * Partial update of a system user (RF09, HU28): a null field means "keep the
 * current value". Only the profile is editable here; the login never changes,
 * the password has its own route and the activation is the deactivation's.
 */
public record UpdateUserCommand(Long id, Role role) {
}
