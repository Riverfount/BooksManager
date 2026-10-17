package com.riverfount.booksmanager.loan.application.port.in;

/** The return of a loan (RF12, HU08). The date is today, from the clock, and the penalty from the configuration. */
public record ReturnLoanCommand(Long loanId) {
}
