package com.riverfount.booksmanager.user.application.port.in;

/** The reader changes their own password while signed in (Phase 6, RF32, RN22). */
public interface ChangeOwnPasswordUseCase {

    /**
     * Replaces the password and revokes every refresh token of the reader, so they sign in again with the new one.
     * A wrong current password is refused and counts toward the login lockout (RNF13).
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader has no access
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the current password is wrong,
     *         the account is locked out or the new password breaks the password policy
     */
    void change(ChangeOwnPasswordCommand command);
}
