package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsQuery;
import com.riverfount.booksmanager.loan.application.port.in.ListSuspensionsUseCase;
import com.riverfount.booksmanager.loan.application.port.in.SuspensionDetails;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries on suspensions (RF22, HU11, RNF14). "In force" is the domain's rule
 * ({@code Suspension.isInForce}) asked for today's date from the clock: a suspension that
 * ended by itself stops being in force with no job to close it, and the same rule is applied
 * by the database filter and by the flag shown on each item.
 */
@Service
@Transactional(readOnly = true)
class SuspensionQueryService implements ListSuspensionsUseCase {

    private final SuspensionRepository suspensionRepository;
    private final Clock clock;

    SuspensionQueryService(SuspensionRepository suspensionRepository, Clock clock) {
        this.suspensionRepository = suspensionRepository;
        this.clock = clock;
    }

    @Override
    public PageResult<SuspensionDetails> list(ListSuspensionsQuery query) {
        var today = LocalDate.now(clock);
        var filter = new SuspensionFilter(query.readerId(), null,
                Boolean.TRUE.equals(query.inForce()) ? today : null,
                Boolean.FALSE.equals(query.inForce()) ? today : null);
        var page = suspensionRepository.findAll(filter, query.page());
        return page.map(suspension -> new SuspensionDetails(suspension, suspension.isInForce(today)));
    }
}
