package com.riverfount.booksmanager.catalog.adapter.out.persistence;

import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.adapter.out.persistence.SpringDataPages;
import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;

    CategoryPersistenceAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Category save(Category category) {
        var entity = new CategoryJpaEntity(category.getId(), category.getName(), category.isActive());
        try {
            // flush so that an UPDATE (rename) also hits the unique
            // constraint here, not only later at commit time
            var saved = jpaRepository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // defense against the race between the service's uniqueness
            // check and this save(): the database's unique constraint is
            // what actually enforces the rule; here we only translate it
            // into the domain exception.
            throw new BusinessRuleException("já existe uma categoria com o nome '" + category.getName() + "'");
        }
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return jpaRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public PageResult<Category> findAll(String nameContains, boolean active, PageQuery page) {
        var pageable = SpringDataPages.toPageable(page);
        var result = nameContains == null
                ? jpaRepository.findByActive(active, pageable)
                : jpaRepository.findByActiveAndNameContainingIgnoreCase(active, nameContains, pageable);
        return SpringDataPages.toPageResult(result, this::toDomain);
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return Category.reconstitute(entity.getId(), entity.getName(), entity.isActive());
    }
}
