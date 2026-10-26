package com.riverfount.booksmanager.user.application.port.in;

/** Grants the access of a reader by invitation (Phase 6, RF08): the reader gets a single-use link by e-mail. */
public interface InviteReaderAccessUseCase {

    /**
     * Sends the invitation, replacing any earlier one still pending.
     *
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the reader does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the reader is inactive or already
     *         has an active access
     */
    ReaderAccessInvitation invite(InviteReaderAccessCommand command);
}
