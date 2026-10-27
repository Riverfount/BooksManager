package com.riverfount.booksmanager.loan.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * How a reader stands today (Phase 6): only what is theirs and what they need to know before trying to borrow or
 * reserve. Nothing about the copies, other readers, the reason of a block (the operator's decision, which is not
 * stored) or the justification of a waived fine.
 *
 * @param canBorrow whether the reader could borrow today as far as they are concerned (the copy and the book are not
 *        part of it): no reason below
 * @param blocked whether an operator blocked the reader (RF10)
 * @param reasons why they cannot borrow, the same ones a loan at the desk would be refused for, in the same order
 *        (RN04, RN05, RN11); empty when {@code canBorrow}
 * @param pendingFineTotal the total of the pending fines (RN05, RF21); zero when there is none
 * @param suspension the suspension in force today, or null (RN15)
 * @param activeLoans how many open loans the reader has
 * @param overdueLoans how many of them are late
 * @param loanLimit how many simultaneous loans the library allows (RN04, from the configuration)
 * @param activeReservations how many active reservations the reader has, counted apart from the loans (RN12)
 * @param reservationLimit how many active reservations the library allows (the loan limit, RN12)
 */
public record ReaderStanding(
        boolean canBorrow,
        boolean blocked,
        List<String> reasons,
        BigDecimal pendingFineTotal,
        SuspensionInForce suspension,
        long activeLoans,
        long overdueLoans,
        int loanLimit,
        long activeReservations,
        int reservationLimit) {

    public ReaderStanding {
        reasons = List.copyOf(reasons);
    }

    /** The days the suspension covers: from {@code startDate} until the day before {@code releaseDate}. */
    public record SuspensionInForce(LocalDate startDate, LocalDate releaseDate) {
    }
}
