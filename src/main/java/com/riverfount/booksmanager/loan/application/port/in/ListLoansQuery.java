package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing loans (RF14, HU30, RNF14). A null filter means "no
 * filter on that field": by reader, by copy and by situation (active or returned). The loans
 * of every situation are listed when no status is given, so the history is a call away.
 */
public record ListLoansQuery(Long readerId, Long copyId, LoanStatus status, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "pickupDate", "dueDate", "returnDate");
    public static final String DEFAULT_SORT = "pickupDate,desc";

    public ListLoansQuery {
        Objects.requireNonNull(page, "page");
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar empréstimos por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }
}
