package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * In-memory implementation of LoanRepository, used only in the application services'
 * unit tests (no Spring, no database). Like the database index, it refuses a second
 * active loan of the same copy (RN03). Does not need to be thread-safe.
 */
class InMemoryLoanRepository implements LoanRepository {

    private static final Map<String, Comparator<Loan>> COMPARATORS = Map.of(
            "id", Comparator.comparing(Loan::getId),
            "pickupDate", Comparator.comparing(Loan::getPickupDate),
            "dueDate", Comparator.comparing(Loan::getDueDate),
            "returnDate", Comparator.comparing(Loan::getReturnDate, Comparator.nullsLast(
                    Comparator.<LocalDate>naturalOrder())));

    private final Map<Long, Loan> loans = new HashMap<>();
    private long nextId = 1;

    /** Like the JPA adapter, the repository never hands out the stored instance. */
    private static Loan copyWithId(Loan loan, Long id) {
        return Loan.reconstitute(id, loan.getCopyId(), loan.getReaderId(), loan.getPickupDate(),
                loan.getDueDate(), loan.getReturnDate(), loan.getRenewals(), loan.getReturnCondition());
    }

    @Override
    public Loan save(Loan loan) {
        var otherActiveOfTheCopy = loans.values().stream().anyMatch(stored -> stored.isActive()
                && stored.getCopyId().equals(loan.getCopyId()) && !stored.getId().equals(loan.getId()));
        if (loan.isActive() && otherActiveOfTheCopy) {
            throw new BusinessRuleException("o exemplar já tem um empréstimo ativo");
        }
        var id = loan.getId() != null ? loan.getId() : nextId++;
        loans.put(id, copyWithId(loan, id));
        return copyWithId(loan, id);
    }

    @Override
    public Optional<Loan> findById(Long id) {
        return Optional.ofNullable(loans.get(id)).map(loan -> copyWithId(loan, id));
    }

    @Override
    public Set<Long> findActiveCopyIdsByReader(Long readerId) {
        return loans.values().stream().filter(loan -> loan.isActive() && loan.getReaderId().equals(readerId))
                .map(Loan::getCopyId).collect(Collectors.toSet());
    }

    @Override
    public long count(LoanFilter filter) {
        return loans.values().stream()
                .filter(loan -> (filter.readerId() == null || loan.getReaderId().equals(filter.readerId()))
                        && (filter.copyId() == null || loan.getCopyId().equals(filter.copyId()))
                        && (filter.active() == null || loan.isActive() == filter.active())
                        && (filter.overdueOn() == null || loan.isOverdue(filter.overdueOn())))
                .count();
    }

    @Override
    public PageResult<Loan> findAll(LoanFilter filter, PageQuery page) {
        var result = InMemoryPages.of(loans.values(),
                loan -> (filter.readerId() == null || loan.getReaderId().equals(filter.readerId()))
                        && (filter.copyId() == null || loan.getCopyId().equals(filter.copyId()))
                        && (filter.active() == null || loan.isActive() == filter.active())
                        && (filter.overdueOn() == null || loan.isOverdue(filter.overdueOn())),
                COMPARATORS, Loan::getId, page);
        return new PageResult<>(result.items().stream().map(loan -> copyWithId(loan, loan.getId())).toList(),
                result.page(), result.size(), result.totalItems());
    }
}
