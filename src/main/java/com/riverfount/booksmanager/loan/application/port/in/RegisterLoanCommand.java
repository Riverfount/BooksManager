package com.riverfount.booksmanager.loan.application.port.in;

/** The loan of a copy to a reader (RF11, HU07). The dates and the period are not asked: they come from the clock and the configuration. */
public record RegisterLoanCommand(Long copyId, Long readerId) {
}
