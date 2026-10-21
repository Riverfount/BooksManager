package com.riverfount.booksmanager.loan.domain;

import java.time.LocalDate;

/**
 * Whether the copy of a loan is being kept for a reservation, and for whom (RN03, RF18). It is one of the
 * facts of a {@link BorrowingSituation}: only the reader who owns the reservation may take a reserved copy,
 * and only up to the pickup deadline, inclusive (RN13).
 */
public enum CopyHold {

    /** The copy is not kept for anyone: it is lent, or refused, by its own status. */
    NONE,

    /** The copy is kept for this reader and the pickup deadline has not passed: this reader may take it. */
    FOR_THIS_READER,

    /**
     * The copy is kept for this reader, but the last day to pick it up has passed. The routine that expires
     * reservations may not have run yet, so the date decides here, not the status of the reservation.
     */
    FOR_THIS_READER_PAST_DEADLINE,

    /** The copy is kept for another reader (or for no active reservation at all): nobody else may take it. */
    FOR_ANOTHER_READER;

    /**
     * What a reserved copy means for the reader who asks to borrow it.
     *
     * @param holding the active reservation that holds the copy, null when none does
     * @param readerId the reader who asks to borrow the copy
     * @param today the day of the loan
     */
    public static CopyHold of(Reservation holding, Long readerId, LocalDate today) {
        if (holding == null || !holding.getReaderId().equals(readerId)) {
            return FOR_ANOTHER_READER;
        }
        return holding.isPastDeadline(today) ? FOR_THIS_READER_PAST_DEADLINE : FOR_THIS_READER;
    }
}
