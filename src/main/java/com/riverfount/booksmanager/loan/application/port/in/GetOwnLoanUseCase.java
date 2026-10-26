package com.riverfount.booksmanager.loan.application.port.in;

/** Shows a loan of the reader, active or returned (Phase 6, RF14). */
public interface GetOwnLoanUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the loan does not exist or
     *         belongs to another reader: the two cases cannot be told apart
     */
    LoanDetails get(GetOwnLoanQuery query);
}
