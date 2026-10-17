package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.domain.Loan;

/**
 * A line of the overdue loans report (RF23, HU12): the late loan, the days it is late, the name of the
 * reader and the summary of the copy. The reader and the copy are null only if their module no longer
 * knows them, which does not happen for a loan that exists; the ids are always in the loan.
 */
public record OverdueLoan(Loan loan, long daysLate, String readerName, CopySummary copy) {
}
