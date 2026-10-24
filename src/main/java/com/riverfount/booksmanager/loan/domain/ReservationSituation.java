package com.riverfount.booksmanager.loan.domain;

/**
 * The facts that decide whether a reader may reserve a book (RF16), gathered by whoever calls the
 * domain and given by parameter, so the rules stay free of any lookup. Each one is already a yes or
 * no about the moment of the reservation; "in force" and "late" use the date of the day.
 *
 * <p>Reserving asks what lending asks, except the availability of a copy and the limit of loans
 * (decided on 17/10/2026), so neither of those is here.
 *
 * @param readerActive the reader is active (RN11)
 * @param readerBlocked the reader was blocked by an operator (RN11)
 * @param activeReservations how many active reservations the reader has, counted apart from loans (RN12)
 * @param hasActiveReservationForBook the reader already has an active reservation of this book (RN12)
 * @param bookActive the book is active (RN14): a book that was inactivated is not reserved
 * @param bookHasAvailableCopy the book has a copy that can be lent now, so it is a case for lending (RN12)
 * @param readerHasBookOnLoan the reader holds an open loan of a copy of this book (RN12)
 * @param hasPendingFine the reader owes a fine that is neither paid nor waived (RN05)
 * @param hasSuspensionInForce the reader is suspended today (RN05)
 * @param hasOverdueLoan the reader holds an open loan past its due date (RN05)
 */
public record ReservationSituation(
        boolean readerActive,
        boolean readerBlocked,
        int activeReservations,
        boolean hasActiveReservationForBook,
        boolean bookActive,
        boolean bookHasAvailableCopy,
        boolean readerHasBookOnLoan,
        boolean hasPendingFine,
        boolean hasSuspensionInForce,
        boolean hasOverdueLoan) {

    public ReservationSituation {
        if (activeReservations < 0) {
            throw new IllegalArgumentException("the number of active reservations cannot be negative");
        }
    }
}
