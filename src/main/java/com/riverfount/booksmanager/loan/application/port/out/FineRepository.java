package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Outbound port for fines (RF21), in the domain's language. */
public interface FineRepository {

    /**
     * Saves the fine and returns it as stored.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the loan already has a fine
     */
    Fine save(Fine fine);

    Optional<Fine> findById(Long id);

    /**
     * The fine of each given loan, in one query (a loan has at most one fine, RN05); a loan
     * without a fine is absent from the map.
     */
    Map<Long, Fine> findByLoanIds(Set<Long> loanIds);

    /** Listing, always paginated (RNF14). */
    PageResult<Fine> findAll(FineFilter filter, PageQuery page);
}
