package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of FineRepository, used only in the application services'
 * unit tests (no Spring, no database). Like the database, it refuses a second fine for
 * the same loan (RN05). Does not need to be thread-safe.
 */
class InMemoryFineRepository implements FineRepository {

    private static final Map<String, Comparator<Fine>> COMPARATORS = Map.of(
            "id", Comparator.comparing(Fine::getId),
            "amount", Comparator.comparing(Fine::getAmount),
            "status", Comparator.comparing(Fine::getStatus));

    private final Map<Long, Fine> fines = new HashMap<>();
    private long nextId = 1;

    /** Like the JPA adapter, the repository never hands out the stored instance. */
    private static Fine copyWithId(Fine fine, Long id) {
        return Fine.reconstitute(id, fine.getLoanId(), fine.getReaderId(), fine.getAmount(), fine.getStatus(),
                fine.getWaiverReason());
    }

    @Override
    public Fine save(Fine fine) {
        var otherFineOfTheLoan = fines.values().stream()
                .anyMatch(stored -> stored.getLoanId().equals(fine.getLoanId()) && !stored.getId().equals(fine.getId()));
        if (otherFineOfTheLoan) {
            throw new BusinessRuleException("o empréstimo já tem multa");
        }
        var id = fine.getId() != null ? fine.getId() : nextId++;
        fines.put(id, copyWithId(fine, id));
        return copyWithId(fine, id);
    }

    @Override
    public Optional<Fine> findById(Long id) {
        return Optional.ofNullable(fines.get(id)).map(fine -> copyWithId(fine, id));
    }

    @Override
    public PageResult<Fine> findAll(FineFilter filter, PageQuery page) {
        var result = InMemoryPages.of(fines.values(),
                fine -> (filter.readerId() == null || fine.getReaderId().equals(filter.readerId()))
                        && (filter.loanId() == null || fine.getLoanId().equals(filter.loanId()))
                        && (filter.status() == null || fine.getStatus() == filter.status()),
                COMPARATORS, Fine::getId, page);
        return new PageResult<>(result.items().stream().map(fine -> copyWithId(fine, fine.getId())).toList(),
                result.page(), result.size(), result.totalItems());
    }
}
