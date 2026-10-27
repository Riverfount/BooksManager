package com.riverfount.booksmanager.loan.domain;

/**
 * The facts about the reader that decide whether a loan of theirs may be renewed (RN21), gathered by whoever calls
 * the domain and given by parameter, like {@link BorrowingSituation}: each one is already a yes or no about the day
 * of the renewal.
 *
 * @param readerActive the reader is active (RN11)
 * @param readerBlocked the reader was blocked by an operator (RF10, RN11)
 * @param hasPendingFine the reader owes a fine that is neither paid nor waived (RN05)
 * @param hasSuspensionInForce the reader is suspended today (RN05)
 */
public record RenewalSituation(
        boolean readerActive,
        boolean readerBlocked,
        boolean hasPendingFine,
        boolean hasSuspensionInForce) {
}
