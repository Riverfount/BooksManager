package com.riverfount.booksmanager.user.application.port.in;

/** Changes the password of a reader with the token of a reset link (Phase 6, RF08). */
public interface ResetReaderPasswordUseCase {

    /**
     * Changes the password, lifts a login lockout and revokes the refresh tokens. An invalid token is refused with
     * one and the same message.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the token is not valid or the
     *         password does not follow the policy
     */
    void reset(ResetReaderPasswordCommand command);
}
