package com.riverfount.booksmanager.user.application.port.in;

/** Creates the access of a reader from a valid invitation (Phase 6, RF08). */
public interface AcceptReaderInvitationUseCase {

    /**
     * Creates the READER user. An unknown, used, expired or revoked invitation is refused with one and the same
     * message, so it does not say which it was; a refused login or password leaves the invitation unused.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the invitation is not valid, the
     *         password does not follow the policy or the login is taken
     */
    void accept(AcceptReaderInvitationCommand command);
}
