package com.riverfount.booksmanager.audit.adapter.out.persistence;

import com.riverfount.booksmanager.audit.application.port.out.AuditEntryRepository;
import com.riverfount.booksmanager.audit.application.port.out.AuditFilter;
import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import org.springframework.stereotype.Component;

@Component
class AuditEntryPersistenceAdapter implements AuditEntryRepository {

    private final AuditEntryJpaRepository jpaRepository;

    AuditEntryPersistenceAdapter(AuditEntryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AuditEntry save(AuditEntry entry) {
        var entity = new AuditEntryJpaEntity(entry.occurredAt(), entry.actorId(), entry.actorLogin(), entry.action(),
                entry.target(), entry.targetId(), entry.summary());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public PageResult<AuditEntry> findAll(AuditFilter filter, PageQuery page) {
        var result = jpaRepository.search(filter.actorId(), filter.action(), filter.target(), filter.targetId(),
                filter.from(), filter.toExclusive(), SpringDataPages.toPageable(page));
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    private AuditEntry toDomain(AuditEntryJpaEntity entity) {
        return AuditEntry.reconstitute(entity.getId(), entity.getOccurredAt(), entity.getActorId(),
                entity.getActorLogin(), entity.getAction(), entity.getTargetType(), entity.getTargetId(),
                entity.getSummary());
    }
}
