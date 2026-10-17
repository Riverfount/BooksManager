package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;

/** Outbound port for fines (RF21), in the domain's language. */
public interface FineRepository {

    /**
     * Saves the fine and returns it as stored.
     *
     * @throws com.riverfount.booksmanager.shared.domain.BusinessRuleException if the loan already has a fine
     */
    Fine save(Fine fine);

    Optional<Fine> findById(Long id);

    /** Listing, always paginated (RNF14). */
    PageResult<Fine> findAll(FineFilter filter, PageQuery page);
}
