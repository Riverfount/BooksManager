package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.util.Objects;

/**
 * The page asked of the overdue loans report (RF23, HU12, RNF14). The order is fixed, from the most to
 * the least late, so there is no sort to choose: only the page and its size (up to 100).
 */
public record ListOverdueLoansQuery(PageQuery page) {

    /** The field that gives the order: the earlier the due date, the later the loan is. */
    static final String ORDER_FIELD = "dueDate";

    public ListOverdueLoansQuery {
        Objects.requireNonNull(page, "page");
    }

    public static ListOverdueLoansQuery of(int page, int size) {
        return new ListOverdueLoansQuery(new PageQuery(page, size, ORDER_FIELD, SortDirection.ASC));
    }
}
