package com.riverfount.booksmanager.loan.application.port.in;

/** The waiver of a fine, with the justification that is required (RF21, HU10). */
public record WaiveFineCommand(Long fineId, String reason) {
}
