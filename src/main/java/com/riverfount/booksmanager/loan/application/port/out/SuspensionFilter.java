package com.riverfount.booksmanager.loan.application.port.out;

import java.time.LocalDate;

/**
 * Optional filters of the suspension listing; a null field means "no filter on that
 * field". {@code inForceOn} keeps only the suspensions in force on that day, with the
 * same meaning as {@code Suspension.isInForce}; {@code notInForceOn} keeps the others
 * (already over, ended early, or not started yet on that day).
 */
public record SuspensionFilter(Long readerId, Long loanId, LocalDate inForceOn, LocalDate notInForceOn) {

    public static SuspensionFilter none() {
        return new SuspensionFilter(null, null, null, null);
    }
}
