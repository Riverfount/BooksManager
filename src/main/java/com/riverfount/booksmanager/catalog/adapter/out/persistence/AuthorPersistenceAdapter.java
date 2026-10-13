package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.AuthorRepository;
import com.riverfount.booksmanager.catalog.domain.Author;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Override
    public PageResult<Author> findAll(String nameContains, boolean active, PageQuery page) {
        var pageable = PageRequest.of(page.page(), page.size(), toSort(page));
        var result = nameContains == null
                ? jpaRepository.findByActive(active, pageable)
                : jpaRepository.findByActiveAndNameContainingIgnoreCase(active, nameContains, pageable);
        return new PageResult<>(result.map(this::toDomain).getContent(), page.page(), page.size(),
                result.getTotalElements());
    }

    // The sortable fields (ListAuthorsQuery.SORTABLE_FIELDS) match the
    // entity's property names. Ties are broken by id so pages are stable.
    private static Sort toSort(PageQuery page) {
        var direction = page.direction() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        var sort = Sort.by(direction, page.sortBy());
        return "id".equals(page.sortBy()) ? sort : sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Author toDomain(AuthorJpaEntity entity) {
        return Author.reconstitute(entity.getId(), entity.getName(), entity.isActive());
    }
}
