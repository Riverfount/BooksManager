package com.riverfount.booksmanager.loan.application.port.in;

/** Returns a loan: closes it, frees the copy and applies the penalty of the current policy (RF12, RF20, HU08). */
public interface ReturnLoanUseCase {

    /**
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the loan does not exist
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the loan was already
     *         returned (RN09)
     * @throws org.springframework.dao.OptimisticLockingFailureException if another request changed the loan or
     *         the copy first
     */
    ReturnLoanResult returnLoan(ReturnLoanCommand command);
}
