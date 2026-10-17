package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * What a return came to (RF12, RF20): the closed loan, the days it was late and the penalty
 * the policy of the moment applied, if any.
 *
 * @param daysLate calendar days past the due date, zero when it came back on time or early
 * @param fine the fine created, null when the policy applied none
 * @param suspension the suspension created, null when the policy applied none
 */
public record ReturnLoanResult(Loan loan, long daysLate, Fine fine, Suspension suspension) {
}
