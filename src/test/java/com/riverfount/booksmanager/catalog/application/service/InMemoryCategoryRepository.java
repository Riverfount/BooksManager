package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.domain.Category;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of CategoryRepository, used only in the
 * application services' unit tests (no Spring, no database). Does not
 * need to be thread-safe: it is a test fake, used from a single thread.
 */
class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> categories = new HashMap<>();
    private long nextId = 1;

    @Override
    public Category save(Category category) {
        var id = category.getId() != null ? category.getId() : nextId++;
        var saved = Category.reconstitute(id, category.getName(), category.isActive());
        categories.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Category> findById(Long id) {
        // same restriction as the real JpaRepository.findById, so that
        // this fake does not let through a scenario that would break in
        // production
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(categories.get(id));
    }

    @Override
    public boolean existsByName(String name) {
        return categories.values().stream().anyMatch(category -> category.getName().equals(name));
    }
}
