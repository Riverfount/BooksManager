package com.riverfount.booksmanager.user.application.port.in;

/**
 * Sets a new password for a system user (RF09, HU29, RNF07). The new password
 * follows the same policy as the creation, is stored only as a hash, and every
 * refresh token of the user is revoked, so a session opened with the old password
 * cannot be renewed.
 */
public interface ResetUserPasswordUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException when the password breaks the policy
     */
    void resetPassword(ResetUserPasswordCommand command);
}
