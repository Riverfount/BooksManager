package com.riverfount.booksmanager.user.application.port.in;

/**
 * An administrator or an operator changes their own password (RF09), the way the reader does (RF32): the current
 * password is asked for, a wrong one counts toward the login lockout, the new one follows the password policy, and every
 * session of the user ends.
 */
public interface ChangeStaffPasswordUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException for an unknown user
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException for a wrong current password, an account
     *         locked out, a new password that breaks the policy, an inactive user or a reader access (which changes
     *         the password in the reader area)
     */
    void change(ChangeStaffPasswordCommand command);
}
