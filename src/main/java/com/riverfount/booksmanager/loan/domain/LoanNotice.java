package com.riverfount.booksmanager.loan.domain;

import java.time.LocalDate;

/**
 * A notice the reader is owed about a loan (RF28), identified by what makes it a different message: its kind, the due
 * date it refers to (a renewal moves it, so the reader is warned again) and its band. The band is 0 for the due-soon
 * notice; for the overdue notice it is the number of whole weeks late, so the reader is told when the loan turns late
 * (band 0, from the 1st late day) and again every seven days (band 1 from the 8th, band 2 from the 15th, and so on).
 * Whoever records which notices went out records this value, and never sends the same one twice.
 */
public record LoanNotice(LoanNoticeKind kind, LocalDate dueDate, int band) {

    /** How many late days one band of the overdue notice covers. */
    public static final int OVERDUE_BAND_DAYS = 7;

    public LoanNotice {
        if (kind == null || dueDate == null) {
            throw new IllegalArgumentException("the kind and the due date of a notice are required");
        }
        if (band < 0) {
            throw new IllegalArgumentException("the band of a notice cannot be negative");
        }
    }
}
