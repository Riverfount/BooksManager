package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * A loan with the result of its penalty, when there is one (RF14, HU30): the fine and the
 * suspension the return created. Both are null for a loan that was not penalized, and always
 * for a loan not yet returned.
 */
public record LoanDetails(Loan loan, Fine fine, Suspension suspension) {
}
