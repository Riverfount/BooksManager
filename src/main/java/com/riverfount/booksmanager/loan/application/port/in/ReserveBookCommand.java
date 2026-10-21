package com.riverfount.booksmanager.loan.application.port.in;

/** The reservation of a book by a reader (RF16). The date comes from the clock, not from the caller. */
public record ReserveBookCommand(Long bookId, Long readerId) {
}
