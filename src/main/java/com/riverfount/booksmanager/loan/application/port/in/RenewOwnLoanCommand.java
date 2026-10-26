package com.riverfount.booksmanager.loan.application.port.in;

/** A reader renews a loan of their own (Phase 6, RF13). No date is accepted: it is today, from the clock. */
public record RenewOwnLoanCommand(Long loanId, Long readerId) {
}
