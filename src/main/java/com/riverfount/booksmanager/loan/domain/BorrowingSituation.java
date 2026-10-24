package com.riverfount.booksmanager.loan.domain;

/**
 * The facts that decide whether a reader may take a copy (RF11), gathered by whoever calls the
 * domain and given by parameter, so the rules stay free of any lookup. Each one is already a
 * yes or no about the moment of the loan: the application finds them (the copy and the reader
 * through the ports of their modules, the loans, fines and suspensions of the reader through
 * this module's repositories), using the date of the day for "in force" and "late".
 *
 * @param copyAvailable the copy exists, is active and has the status available (RN03)
 * @param copyHold whether the copy is kept for a reservation and for whom (RN03, RF18); a reserved copy is
 *        never "available", and the one who owns the reservation may take it all the same
 * @param bookActive the book of the copy is active (RN14): the copies of a book that was inactivated are not lent,
 *        and that includes the pickup of a copy kept for a reservation
 * @param readerActive the reader is active (RN11)
 * @param readerBlocked the reader was blocked by an operator (RN11)
 * @param activeLoans how many loans of the reader are open now (RN04)
 * @param hasPendingFine the reader owes a fine that is neither paid nor waived (RN05)
 * @param hasSuspensionInForce the reader is suspended today (RN05)
 * @param hasOverdueLoan the reader holds an open loan past its due date (RN05)
 */
public record BorrowingSituation(
        boolean copyAvailable,
        CopyHold copyHold,
        boolean bookActive,
        boolean readerActive,
        boolean readerBlocked,
        int activeLoans,
        boolean hasPendingFine,
        boolean hasSuspensionInForce,
        boolean hasOverdueLoan) {

    public BorrowingSituation {
        if (copyHold == null) {
            throw new IllegalArgumentException("the hold on the copy is required");
        }
        if (activeLoans < 0) {
            throw new IllegalArgumentException("the number of active loans cannot be negative");
        }
    }
}
