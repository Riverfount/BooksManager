package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Pagination and optional filters for the copy listing (RF04): by book
 * (null means any book), by status (null means any status) and by asset
 * code (the whole code, without caring about case; null or blank means any
 * code). The asset code is how a screen finds the copy a clerk typed.
 */
public record ListCopiesQuery(Long bookId, CopyStatus status, String assetCode, boolean active, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "assetCode", "status");
    public static final String DEFAULT_SORT = "assetCode";

    public ListCopiesQuery {
        Objects.requireNonNull(page, "page");
        assetCode = assetCode == null || assetCode.isBlank() ? null : assetCode.strip();
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar exemplares por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }

    /** Without the asset code filter. */
    public ListCopiesQuery(Long bookId, CopyStatus status, boolean active, PageQuery page) {
        this(bookId, status, null, active, page);
    }
}
