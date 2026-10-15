package com.riverfount.booksmanager.user.application.port.in;

/**
 * Deactivation of a system user (RF09, HU28): who is deactivated and who is
 * doing it, so that nobody can deactivate themselves.
 */
public record DeactivateUserCommand(Long id, Long actingUserId) {
}
