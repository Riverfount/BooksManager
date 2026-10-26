package com.riverfount.booksmanager.loan.application.port.in;

/** A reader reserves a book for themselves (Phase 6, RF16). The reader is the one of the token, never one sent. */
public record ReserveOwnBookCommand(Long bookId, Long readerId) {
}
