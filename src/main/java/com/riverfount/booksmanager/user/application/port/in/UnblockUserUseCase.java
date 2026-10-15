package com.riverfount.booksmanager.user.application.port.in;

/**
 * Lifts the login lockout of a user right away (RF08, RF09), for an
 * administrator: the count of wrong passwords and the block are cleared, so the
 * right password logs in at once. Idempotent: a user who is not blocked and has
 * no wrong passwords is left as is.
 */
public interface UnblockUserUseCase {

    /** @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user */
    void unblock(Long id);
}
