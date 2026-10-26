package com.riverfount.booksmanager.loan.application.port.in;

/**
 * A reader renews a loan of their own (Phase 6, RF13), under the same rules as the renewal at the desk: not when it
 * is late, closed, of an inactive book, with the limit reached, or with another reader waiting in the queue for the
 * book (RN07).
 */
public interface RenewOwnLoanUseCase {

    /**
     * @return the loan as the reader sees it, with the new due date
     * @throws com.riverfount.booksmanager.shared.domain.ResourceNotFoundException if the loan does not exist or
     *         belongs to another reader (checked before anything else, so the state of the loan of another reader
     *         is never told)
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the renewal is not allowed
     */
    LoanDetails renew(RenewOwnLoanCommand command);
}
