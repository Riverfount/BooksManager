package com.riverfount.booksmanager.user.application.port.in;

/** Invites a reader to have access to the system (Phase 6): the invitation goes to the reader's own e-mail. */
public record InviteReaderAccessCommand(Long readerId) {
}
