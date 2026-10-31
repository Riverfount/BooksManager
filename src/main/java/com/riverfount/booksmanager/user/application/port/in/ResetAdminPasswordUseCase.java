package com.riverfount.booksmanager.user.application.port.in;

/**
 * Recovers the access of an administrator who forgot the password, from the environment at startup (RF08, RF09). It
 * is for the case where no other administrator can reset the password in the Users screen.
 */
public interface ResetAdminPasswordUseCase {

    /**
     * Replaces the password of the active administrator with the given login, clears the login block and revokes the
     * refresh tokens, and reports it to the audit trail without an actor and without any secret. Anything that is not
     * an active administrator is left untouched.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException when the password breaks the policy
     */
    AdminPasswordResetResult reset(ResetAdminPasswordCommand command);
}
