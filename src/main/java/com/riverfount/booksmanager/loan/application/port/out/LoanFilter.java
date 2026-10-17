package com.riverfount.booksmanager.loan.application.port.out;

import java.time.LocalDate;

/**
 * Optional filters of the loan listing (RF14); a null field means "no filter on that
 * field". {@code active} is the situation: true for the loans not yet returned, false
 * for the returned ones. {@code overdueOn} keeps only the loans that are late on that
 * day, with the same meaning as {@code Loan.isOverdue}: still open, with the due date
 * before the day.
 */
public record LoanFilter(Long readerId, Long copyId, Boolean active, LocalDate overdueOn) {

    public static LoanFilter none() {
        return new LoanFilter(null, null, null, null);
    }
}
