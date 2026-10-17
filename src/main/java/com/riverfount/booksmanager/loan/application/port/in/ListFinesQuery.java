package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.FineStatus;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing fines (RF22, HU11, RNF14). A null filter means "no
 * filter on that field": by reader and by situation (pending, paid or waived).
 */
public record ListFinesQuery(Long readerId, FineStatus status, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "amount", "status");
    public static final String DEFAULT_SORT = "id,desc";

    public ListFinesQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar multas por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
