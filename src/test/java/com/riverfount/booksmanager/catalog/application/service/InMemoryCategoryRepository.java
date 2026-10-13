package com.riverfount.booksmanager.catalog.application.service;

import com.riverfount.booksmanager.catalog.application.port.out.CategoryRepository;
import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
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

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return categories.values().stream()
                .anyMatch(category -> category.getName().equals(name) && !category.getId().equals(id));
    }

    @Override
    public PageResult<Category> findAll(String nameContains, boolean active, PageQuery page) {
        var matching = categories.values().stream()
                .filter(category -> category.isActive() == active)
                .filter(category -> nameContains == null || category.getName().toLowerCase(Locale.ROOT)
                        .contains(nameContains.toLowerCase(Locale.ROOT)));
        return InMemoryPagination.page(matching, page, Map.of(
                "id", Comparator.comparing(Category::getId),
                "name", Comparator.comparing(Category::getName)), Comparator.comparing(Category::getId));
    }
}
