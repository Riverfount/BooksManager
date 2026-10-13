package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing categories (RF03, HU20). A blank
 * name means no name filter.
 */
public record ListCategoriesQuery(String name, boolean active, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "name");
    public static final String DEFAULT_SORT = "name";

    public ListCategoriesQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar categorias por: " + page.sortBy()
                    + " (use " + String.join(" ou ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
        name = name == null || name.isBlank() ? null : name.strip();
    }
}
