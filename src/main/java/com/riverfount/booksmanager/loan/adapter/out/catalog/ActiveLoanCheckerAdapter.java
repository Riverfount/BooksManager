package com.riverfount.booksmanager.loan.adapter.out.catalog;

import com.riverfount.booksmanager.catalog.application.port.out.ActiveLoanChecker;
import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import org.springframework.stereotype.Component;

/**
 * Answers the catalog's question "does this copy have an active loan?" (RN10) from the loan
 * module's own repository. The loan is the source of truth: it does not rely on the status of
 * the copy, which is only a consequence of the loan.
 */
@Component
class ActiveLoanCheckerAdapter implements ActiveLoanChecker {

    private static final PageQuery JUST_THE_COUNT = PageQuery.of(0, 1, "id");

    private final LoanRepository loanRepository;

    ActiveLoanCheckerAdapter(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    @Override
    public boolean hasActiveLoan(Long copyId) {
        return loanRepository.findAll(new LoanFilter(null, copyId, true, null), JUST_THE_COUNT).totalItems() > 0;
    }
}
