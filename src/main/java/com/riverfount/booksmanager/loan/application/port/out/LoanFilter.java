package com.riverfount.booksmanager.loan.application.port.out;

/**
 * Optional filters of the loan listing (RF14); a null field means "no filter on that
 * field". {@code active} is the situation: true for the loans not yet returned, false
 * for the returned ones.
 */
public record LoanFilter(Long readerId, Long copyId, Boolean active) {

    public static LoanFilter none() {
        return new LoanFilter(null, null, null);
    }
}
