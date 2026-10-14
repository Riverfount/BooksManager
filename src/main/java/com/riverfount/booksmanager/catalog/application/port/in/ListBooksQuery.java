package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Pagination and optional filters of the book listing and search (RF01,
 * RF05, HU03): by title, author name, ISBN (all "contains", ignoring case)
 * and category id. A null filter means "no filter on that field".
 */
public record ListBooksQuery(String title, String author, String isbn, Long categoryId, boolean active,
        PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "title", "isbn", "publicationYear");
    public static final String DEFAULT_SORT = "title";

    public ListBooksQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar livros por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }

    /** Listing with no search filter. */
    public ListBooksQuery(boolean active, PageQuery page) {
        this(null, null, null, null, active, page);
    }
}
