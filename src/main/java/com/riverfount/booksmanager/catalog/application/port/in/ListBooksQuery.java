package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Pagination for the plain book listing (RF01). The search by title,
 * author, ISBN and category with available copies (RF05, HU03) is a
 * separate use case.
 */
public record ListBooksQuery(boolean active, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "title", "isbn", "publicationYear");
    public static final String DEFAULT_SORT = "title";

    public ListBooksQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar livros por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
