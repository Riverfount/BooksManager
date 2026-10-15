package com.riverfount.booksmanager.user.application.port.in;

/**
 * Deactivates a system user (RF09, HU28, RN14): the record stays, the user can
 * no longer log in or renew a token, and the refresh tokens are revoked.
 * Idempotent: deactivating an inactive user changes nothing.
 */
public interface DeactivateUserUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException when a user would deactivate
     *         themselves or the last active administrator would be deactivated
     */
    void deactivate(DeactivateUserCommand command);
}
