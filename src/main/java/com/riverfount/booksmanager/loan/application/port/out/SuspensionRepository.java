package com.riverfount.booksmanager.loan.application.port.out;

import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Outbound port for suspensions (RF20, RN15), in the domain's language. */
public interface SuspensionRepository {

    /** Saves the suspension and returns it as stored. */
    Suspension save(Suspension suspension);

    Optional<Suspension> findById(Long id);

    /**
     * The suspension of each given loan, in one query; a loan without one is absent from the
     * map. A return creates at most one suspension, but if a loan ever has more, the latest
     * (highest id) is the one kept.
     */
    Map<Long, Suspension> findByLoanIds(Set<Long> loanIds);

    /**
     * How many different readers have a suspension in force on the day, in one aggregated query. A suspension
     * is in force with the meaning of {@code Suspension.isInForce}; a reader with two counts once.
     */
    long countReadersInForceOn(LocalDate day);

    /** Listing, always paginated (RNF14). */
    PageResult<Suspension> findAll(SuspensionFilter filter, PageQuery page);
}
