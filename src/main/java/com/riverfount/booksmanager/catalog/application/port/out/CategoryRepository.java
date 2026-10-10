package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.Category;
import java.util.Optional;

/**
 * Outbound port for Category persistence, in the domain's language.
 */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(Long id);

    boolean existsByName(String name);
}
