package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * The copies the reader holds on loan right now (loans not yet returned), in one query. A reader has
     * at most as many as the loan limit of the configuration, so the answer is small and not paginated.
     */
    Set<Long> findActiveCopyIdsByReader(Long readerId);

    /**
     * How many loans each copy had in the period, in one aggregated query (RF24): the loans picked up from
     * {@code from} to {@code to}, both inclusive. A renewal moves the due date, not the pickup date, so it is not
     * a new loan. A copy with no loan in the period is absent. The order is not defined.
     */
    List<CopyLoanCount> countLoansByCopy(LocalDate from, LocalDate to);

    /** How many loans the filter keeps, in one aggregated query: the same total {@link #findAll} gives. */
    long count(LoanFilter filter);
}
