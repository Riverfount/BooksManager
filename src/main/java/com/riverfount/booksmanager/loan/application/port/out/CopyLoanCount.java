package com.riverfount.booksmanager.loan.application.port.out;

/** How many loans a copy had in a period (RF24): the loan module knows the copy, not its book. */
public record CopyLoanCount(Long copyId, long total) {
}
