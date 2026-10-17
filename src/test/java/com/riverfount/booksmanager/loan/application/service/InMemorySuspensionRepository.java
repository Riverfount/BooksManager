package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of SuspensionRepository, used only in the application
 * services' unit tests (no Spring, no database). "In force on a day" is the domain's own
 * {@code isInForce}. Does not need to be thread-safe.
 */
class InMemorySuspensionRepository implements SuspensionRepository {

    private static final Map<String, Comparator<Suspension>> COMPARATORS = Map.of(
            "id", Comparator.comparing(Suspension::getId),
            "startDate", Comparator.comparing(Suspension::getStartDate),
            "endDate", Comparator.comparing(Suspension::getEndDate));

    private final Map<Long, Suspension> suspensions = new HashMap<>();
    private long nextId = 1;

    /** Like the JPA adapter, the repository never hands out the stored instance. */
    private static Suspension copyWithId(Suspension suspension, Long id) {
        return Suspension.reconstitute(id, suspension.getReaderId(), suspension.getLoanId(),
                suspension.getStartDate(), suspension.getEndDate(), suspension.getEndedEarlyOn(),
                suspension.getEndedEarlyReason());
    }

    @Override
    public Suspension save(Suspension suspension) {
        var id = suspension.getId() != null ? suspension.getId() : nextId++;
        suspensions.put(id, copyWithId(suspension, id));
        return copyWithId(suspension, id);
    }

    @Override
    public Optional<Suspension> findById(Long id) {
        return Optional.ofNullable(suspensions.get(id)).map(suspension -> copyWithId(suspension, id));
    }

    @Override
    public PageResult<Suspension> findAll(SuspensionFilter filter, PageQuery page) {
        var result = InMemoryPages.of(suspensions.values(),
                suspension -> (filter.readerId() == null || suspension.getReaderId().equals(filter.readerId()))
                        && (filter.loanId() == null || suspension.getLoanId().equals(filter.loanId()))
                        && (filter.inForceOn() == null || suspension.isInForce(filter.inForceOn())),
                COMPARATORS, Suspension::getId, page);
        return new PageResult<>(result.items().stream()
                .map(suspension -> copyWithId(suspension, suspension.getId())).toList(),
                result.page(), result.size(), result.totalItems());
    }
}
