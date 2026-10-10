package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
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
        var entity = new AuthorJpaEntity(author.getId(), author.getName(), author.isActive());
        var saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Author> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Author> findAllByIds(Set<Long> ids) {
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    private Author toDomain(AuthorJpaEntity entity) {
        return Author.reconstitute(entity.getId(), entity.getName(), entity.isActive());
    }
}
