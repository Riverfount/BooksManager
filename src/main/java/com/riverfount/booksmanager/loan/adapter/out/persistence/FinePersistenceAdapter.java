package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.FineFilter;
import com.riverfount.booksmanager.loan.application.port.out.FineRepository;
import com.riverfount.booksmanager.loan.domain.Fine;
import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.shared.adapter.out.persistence.ConstraintViolations;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class FinePersistenceAdapter implements FineRepository {

    private final FineJpaRepository jpaRepository;

    FinePersistenceAdapter(FineJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public BigDecimal sumPendingAmount(Long readerId) {
        return jpaRepository.sumAmountByReaderAndStatus(readerId, FineStatus.PENDING);
    }

    @Override
    public Fine save(Fine fine) {
        var entity = fine.getId() == null ? create(fine) : update(fine);
        try {
            // flushed here so that the violation of the unique loan fails inside this try
            return toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // the unique loan_id is what actually enforces one fine per loan (RN05)
            if (ConstraintViolations.violates(e, "fine_loan_id_key")) {
                throw new BusinessRuleException("o empréstimo já tem multa");
            }
            throw e;
        }
    }

    @Override
    public Optional<Fine> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Map<Long, Fine> findByLoanIds(Set<Long> loanIds) {
        if (loanIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findByLoanIdIn(loanIds).stream()
                .collect(Collectors.toMap(FineJpaEntity::getLoanId, this::toDomain));
    }

    @Override
    public PageResult<Fine> findAll(FineFilter filter, PageQuery page) {
        var result = jpaRepository.search(filter.readerId(), filter.loanId(), filter.status(),
                SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    private static FineJpaEntity create(Fine fine) {
        return new FineJpaEntity(fine.getLoanId(), fine.getReaderId(), fine.getAmount(), fine.getStatus(),
                fine.getWaiverReason());
    }

    /** Updates the entity loaded in the current transaction, so its version is the one the use case read. */
    private FineJpaEntity update(Fine fine) {
        var entity = jpaRepository.findById(fine.getId()).orElseThrow(
                () -> new IllegalStateException("the fine " + fine.getId() + " does not exist"));
        entity.update(fine.getStatus(), fine.getWaiverReason());
        return entity;
    }

    private Fine toDomain(FineJpaEntity entity) {
        return Fine.reconstitute(entity.getId(), entity.getLoanId(), entity.getReaderId(), entity.getAmount(),
                entity.getStatus(), entity.getWaiverReason());
    }
}
