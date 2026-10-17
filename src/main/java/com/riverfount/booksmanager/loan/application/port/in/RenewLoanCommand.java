package com.riverfount.booksmanager.loan.application.port.in;

/** The renewal of an active loan (RF13, HU09). The date is today, from the clock, and the limit and period from the configuration. */
public record RenewLoanCommand(Long loanId) {
}
