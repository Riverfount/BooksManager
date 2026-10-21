package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Reservation;
import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * What a return came to (RF12, RF20): the closed loan, the days it was late and the penalty
 * the policy of the moment applied, if any, and the reservation the returned copy went to, if any (RF18).
 *
 * @param daysLate calendar days past the due date, zero when it came back on time or early
 * @param fine the fine created, null when the policy applied none
 * @param suspension the suspension created, null when the policy applied none
 * @param reservation the reservation that got the copy, null when the copy stayed available (nobody was
 *        waiting, or it came back damaged or lost)
 */
public record ReturnLoanResult(Loan loan, long daysLate, Fine fine, Suspension suspension,
        Reservation reservation) {
}
