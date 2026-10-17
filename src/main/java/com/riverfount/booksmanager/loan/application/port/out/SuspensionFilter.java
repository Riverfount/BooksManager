package com.riverfount.booksmanager.loan.application.port.out;

import java.time.LocalDate;

/**
 * Optional filters of the suspension listing; a null field means "no filter on that
 * field". {@code inForceOn} keeps only the suspensions in force on that day, with the
 * same meaning as {@code Suspension.isInForce}.
 */
public record SuspensionFilter(Long readerId, Long loanId, LocalDate inForceOn) {

    public static SuspensionFilter none() {
        return new SuspensionFilter(null, null, null);
    }
}
