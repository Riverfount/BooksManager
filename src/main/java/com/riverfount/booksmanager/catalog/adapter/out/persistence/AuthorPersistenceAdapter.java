package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class AuthorPersistenceAdapter implements AuthorRepository {

    private final AuthorJpaRepository jpaRepository;

    AuthorPersistenceAdapter(AuthorJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Author save(Author author) {
        var entity = author.getId() == null ? new AuthorJpaEntity(null, author.getName(), author.isActive())
                : update(author);
        // saveAndFlush, not save: the optimistic lock (RNF17) fails here, synchronously
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Author> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Author> findAllByIds(Set<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Author> findActiveByNames(Set<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        var lowerNames = names.stream().map(name -> name.toLowerCase(java.util.Locale.ROOT)).collect(
                java.util.stream.Collectors.toSet());
        return jpaRepository.findActiveByLowerNames(lowerNames).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<Author> findAll(String nameContains, boolean active, PageQuery page) {
        var pageable = SpringDataPages.toPageable(page);
        var result = nameContains == null
                ? jpaRepository.findByActive(active, pageable)
                : jpaRepository.findByActiveAndNameContainingIgnoreCase(active, nameContains, pageable);
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    /** Changes the entity that the use case loaded in this transaction, so its version is the one that is checked. */
    private AuthorJpaEntity update(Author author) {
        var entity = jpaRepository.findById(author.getId()).orElseThrow(
                () -> new IllegalStateException("the author " + author.getId() + " does not exist"));
        entity.update(author.getName(), author.isActive());
        return entity;
    }

    private Author toDomain(AuthorJpaEntity entity) {
        return Author.reconstitute(entity.getId(), entity.getName(), entity.isActive());
    }
}
