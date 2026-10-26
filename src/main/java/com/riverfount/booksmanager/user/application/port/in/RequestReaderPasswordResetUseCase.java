package com.riverfount.booksmanager.user.application.port.in;

/** Sends a password reset link to the reader (Phase 6, RF08). */
public interface RequestReaderPasswordResetUseCase {

    /**
     * Sends the link to every active reader with that e-mail who has an active access. It never says whether
     * anything was sent (the caller cannot tell a registered e-mail from one that is not).
     */
    void requestReset(RequestReaderPasswordResetCommand command);
}
