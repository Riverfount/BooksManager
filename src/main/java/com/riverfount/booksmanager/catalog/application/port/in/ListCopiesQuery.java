package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Pagination and optional filters for the copy listing (RF04): by book
 * (null means any book) and by status (null means any status).
 */
public record ListCopiesQuery(Long bookId, CopyStatus status, boolean active, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "assetCode", "status");
    public static final String DEFAULT_SORT = "assetCode";

    public ListCopiesQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar exemplares por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
