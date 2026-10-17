package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;

/** Outbound port for loans (RF11, RF14), in the domain's language. */
public interface LoanRepository {

    /**
     * Saves the loan and returns it as stored.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the copy already has an
     *         active loan (RN03); the database enforces it, so it also holds when two requests race
     */
    Loan save(Loan loan);

    Optional<Loan> findById(Long id);

    /** Listing, always paginated (RNF14). */
    PageResult<Loan> findAll(LoanFilter filter, PageQuery page);

    /** How many loans the filter keeps, in one aggregated query: the same total {@link #findAll} gives. */
    long count(LoanFilter filter);
}
