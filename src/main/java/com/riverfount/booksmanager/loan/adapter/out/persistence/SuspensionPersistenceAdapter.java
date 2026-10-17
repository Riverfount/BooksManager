package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.application.port.out.SuspensionFilter;
import com.riverfount.booksmanager.loan.application.port.out.SuspensionRepository;
import com.riverfount.booksmanager.loan.domain.Suspension;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class SuspensionPersistenceAdapter implements SuspensionRepository {

    private final SuspensionJpaRepository jpaRepository;

    SuspensionPersistenceAdapter(SuspensionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Suspension save(Suspension suspension) {
        var entity = suspension.getId() == null ? create(suspension) : update(suspension);
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Suspension> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Map<Long, Suspension> findByLoanIds(Set<Long> loanIds) {
        if (loanIds.isEmpty()) {
            return Map.of();
        }
        // ordered by id, so when a loan has more than one the latest one wins
        return jpaRepository.findByLoanIdInOrderByIdAsc(loanIds).stream()
                .collect(Collectors.toMap(SuspensionJpaEntity::getLoanId, this::toDomain, (older, latest) -> latest));
    }

    @Override
    public PageResult<Suspension> findAll(SuspensionFilter filter, PageQuery page) {
        var result = jpaRepository.search(filter.readerId(), filter.loanId(), filter.inForceOn(),
                SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    private static SuspensionJpaEntity create(Suspension suspension) {
        return new SuspensionJpaEntity(suspension.getReaderId(), suspension.getLoanId(),
                suspension.getStartDate(), suspension.getEndDate(), suspension.getEndedEarlyOn(),
                suspension.getEndedEarlyReason());
    }

    /** Updates the entity loaded in the current transaction, so its version is the one the use case read. */
    private SuspensionJpaEntity update(Suspension suspension) {
        var entity = jpaRepository.findById(suspension.getId()).orElseThrow(
                () -> new IllegalStateException("the suspension " + suspension.getId() + " does not exist"));
        entity.update(suspension.getEndedEarlyOn(), suspension.getEndedEarlyReason());
        return entity;
    }

    private Suspension toDomain(SuspensionJpaEntity entity) {
        return Suspension.reconstitute(entity.getId(), entity.getReaderId(), entity.getLoanId(),
                entity.getStartDate(), entity.getEndDate(), entity.getEndedEarlyOn(), entity.getEndedEarlyReason());
    }
}
