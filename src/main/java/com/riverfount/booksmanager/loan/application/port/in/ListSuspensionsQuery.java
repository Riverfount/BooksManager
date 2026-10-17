package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing suspensions (RF22, HU11, RNF14). A null filter means "no
 * filter on that field". {@code inForce} true keeps the suspensions in force today, false the
 * others (already over, ended early or not started yet); "today" is the clock's, not asked.
 */
public record ListSuspensionsQuery(Long readerId, Boolean inForce, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "startDate", "endDate");
    public static final String DEFAULT_SORT = "startDate,desc";

    public ListSuspensionsQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar suspensões por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
