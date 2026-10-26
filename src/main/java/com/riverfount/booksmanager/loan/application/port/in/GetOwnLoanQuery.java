package com.riverfount.booksmanager.loan.application.port.in;

/** One loan of the reader (Phase 6). */
public record GetOwnLoanQuery(Long loanId, Long readerId) {
}
