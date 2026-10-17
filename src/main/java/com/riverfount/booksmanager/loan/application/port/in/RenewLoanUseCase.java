package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Loan;

/** Renews an active loan (RF13, HU09). */
public interface RenewLoanUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the loan does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the loan is closed (RN09), late,
     *         or already renewed as many times as the library allows (RN07)
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the loan first
     */
    Loan renew(RenewLoanCommand command);
}
