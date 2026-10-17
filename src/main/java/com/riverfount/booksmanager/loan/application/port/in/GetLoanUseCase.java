package com.riverfount.booksmanager.loan.application.port.in;

import java.util.Optional;

/** Finds a loan, active or closed, with the result of its penalty (RF14, HU30). */
public interface GetLoanUseCase {

    Optional<LoanDetails> findById(Long id);
}
