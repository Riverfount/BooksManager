package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Category;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;
import java.util.Optional;

/**
 * Outbound port for Category persistence, in the domain's language.
 */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    boolean existsByName(String name);

    /** Whether a category other than the given one already has this name. */
    boolean existsByNameAndIdNot(String name, Long id);

    /**
     * Lists categories by active flag and, when nameContains is not null,
     * by name containing it (case-insensitive). The sort field is one of
     * ListCategoriesQuery.SORTABLE_FIELDS.
     */
    PageResult<Category> findAll(String nameContains, boolean active, PageQuery page);
}
