package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.LoanFilter;
import com.riverfount.booksmanager.loan.application.port.out.LoanRepository;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class LoanPersistenceAdapter implements LoanRepository {

    private final LoanJpaRepository jpaRepository;

    LoanPersistenceAdapter(LoanJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Loan save(Loan loan) {
        var entity = loan.getId() == null ? create(loan) : update(loan);
        try {
            // flushed here so that the violation of the active loan index fails inside this try
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // the partial unique index is what actually enforces one active loan per
            // copy (RN03, RNF17), including the race between two requests; here we
            // only translate it, and only when the violation really is that one.
            if (ConstraintViolations.violates(e, "uq_loan_active_copy")) {
                throw new BusinessRuleException("o exemplar já tem um empréstimo ativo");
            }
            throw e;
        }
    }

    @Override
    public Optional<Loan> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Loan> findAll(LoanFilter filter, PageQuery page) {
        var result = jpaRepository.search(filter.readerId(), filter.copyId(), filter.active(),
                filter.overdueOn(), SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    @Override
    public long count(LoanFilter filter) {
        return jpaRepository.countMatching(filter.readerId(), filter.copyId(), filter.active(),
                filter.overdueOn());
    }

    private static LoanJpaEntity create(Loan loan) {
        return new LoanJpaEntity(loan.getCopyId(), loan.getReaderId(), loan.getPickupDate(), loan.getDueDate(),
                loan.getReturnDate(), loan.getReturnCondition(), loan.getRenewals());
    }

    /**
     * Updates the entity loaded in the current transaction instead of building a new one,
     * so its version is the one read by the use case and a concurrent change fails the save.
     */
    private LoanJpaEntity update(Loan loan) {
        var entity = jpaRepository.findById(loan.getId()).orElseThrow(
                () -> new IllegalStateException("the loan " + loan.getId() + " does not exist"));
        entity.update(loan.getDueDate(), loan.getReturnDate(), loan.getReturnCondition(), loan.getRenewals());
        return entity;
    }

    private Loan toDomain(LoanJpaEntity entity) {
        return Loan.reconstitute(entity.getId(), entity.getCopyId(), entity.getReaderId(), entity.getPickupDate(),
                entity.getDueDate(), entity.getReturnDate(), entity.getRenewals(), entity.getReturnCondition());
    }
}
