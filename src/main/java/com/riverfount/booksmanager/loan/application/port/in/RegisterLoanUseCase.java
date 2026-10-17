package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Loan;

/** Registers the loan of a copy to a reader (RF11, HU07). */
public interface RegisterLoanUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the copy or the reader
     *         does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException with every reason when the loan is
     *         not allowed (RN03, RN04, RN05, RN11)
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the copy first
     */
    Loan register(RegisterLoanCommand command);
}
