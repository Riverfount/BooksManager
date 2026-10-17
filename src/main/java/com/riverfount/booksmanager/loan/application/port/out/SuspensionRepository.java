package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;

/** Outbound port for suspensions (RF20, RN15), in the domain's language. */
public interface SuspensionRepository {

    /** Saves the suspension and returns it as stored. */
    Suspension save(Suspension suspension);

    Optional<Suspension> findById(Long id);

    /** Listing, always paginated (RNF14). */
    PageResult<Suspension> findAll(SuspensionFilter filter, PageQuery page);
}
