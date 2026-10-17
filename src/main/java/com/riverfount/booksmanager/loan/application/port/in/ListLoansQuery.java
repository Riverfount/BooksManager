package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import java.util.Objects;
import java.util.Set;

/**
 * Filters and pagination for listing loans (RF14, HU30, RNF14). A null filter means "no
 * filter on that field": by reader, by copy and by situation (active or returned). The loans
 * of every situation are listed when no status is given, so the history is a call away.
 *
 * <p>{@code overdue} keeps only the active loans past their due date on the clock's day, with the
 * same meaning as the overdue report (HU12); a loan due today is still on time. It makes no sense
 * for the returned loans, so asking for both is refused.
 */
public record ListLoansQuery(Long readerId, Long copyId, LoanStatus status, boolean overdue, PageQuery page) {

    public static final Set<String> SORTABLE_FIELDS = Set.of("id", "pickupDate", "dueDate", "returnDate");
    public static final String DEFAULT_SORT = "pickupDate,desc";

    public ListLoansQuery {
        Objects.requireNonNull(page, "page");
        if (overdue && status == LoanStatus.RETURNED) {
            throw new InvalidQueryException("não há empréstimo atrasado entre os devolvidos: use overdue com "
                    + "status ACTIVE ou sem status (situação pedida: RETURNED)");
        }
        if (!SORTABLE_FIELDS.contains(page.sortBy())) {
            throw new InvalidQueryException("não é possível ordenar empréstimos por: " + page.sortBy()
                    + " (use " + String.join(", ", SORTABLE_FIELDS.stream().sorted().toList()) + ")");
        }
    }

    /** Without the overdue filter. */
    public ListLoansQuery(Long readerId, Long copyId, LoanStatus status, PageQuery page) {
        this(readerId, copyId, status, false, page);
    }
}
