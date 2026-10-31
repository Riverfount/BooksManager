package com.riverfount.booksmanager.user.application.port.in;

import com.riverfount.booksmanager.user.domain.User;

/**
 * Reactivates an inactivated system user, administrator or operator (RF09), for an administrator: the user logs in
 * again with the same password. The refresh tokens revoked at the inactivation stay revoked. Idempotent: reactivating
 * an active user changes nothing and reports nothing.
 */
public interface ReactivateUserUseCase {

    /**
     * @return the user, active
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException for a reader access, which is reopened by
     *         a new invitation
     */
    User reactivate(Long id);
}
