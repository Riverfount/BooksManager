package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * A loan with the result of its penalty, when there is one (RF14, HU30): the fine and the
 * suspension the return created. Both are null for a loan that was not penalized, and always
 * for a loan not yet returned. It also carries the name of the reader and the summary of the copy, so a
 * screen needs no extra call per loan; they are null only if their module no longer knows them, which
 * does not happen for a loan that exists (the ids are always in the loan).
 *
 * <p>{@code daysLate} is the days past the due date: for a returned loan, up to the day it came back
 * (zero when it was on time); for an active loan, up to the clock's day, and only when it is late
 * (null while it is on time, a loan due today included).
 */
public record LoanDetails(Loan loan, Fine fine, Suspension suspension, String readerName, CopySummary copy,
        Long daysLate) {
}
