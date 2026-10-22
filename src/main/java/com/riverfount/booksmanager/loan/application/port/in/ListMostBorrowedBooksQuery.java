package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The period and the page asked of the most borrowed books report (RF24, RNF14). The period is required, with
 * both ends inclusive, in order ({@code from} not after {@code to}) and no longer than
 * {@value #MAX_PERIOD_DAYS} days, so a request cannot ask the whole history at once. The order of the report is
 * fixed (most borrowed first), so there is no sort to choose: only the page and its size (up to 100).
 */
public record ListMostBorrowedBooksQuery(LocalDate from, LocalDate to, PageQuery page) {

    /** The longest period accepted, both ends counted: a year, leap year included. */
    public static final int MAX_PERIOD_DAYS = 366;

    private static final String ORDER_FIELD = "totalLoans";

    public ListMostBorrowedBooksQuery {
        if (from == null || to == null) {
            throw new InvalidQueryException("o período é obrigatório: informe from e to (AAAA-MM-DD)");
        }
        if (from.isAfter(to)) {
            throw new InvalidQueryException("o período está invertido: from (" + from + ") é depois de to (" + to + ")");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            throw new InvalidQueryException("o período é longo demais: no máximo " + MAX_PERIOD_DAYS + " dias");
        }
        Objects.requireNonNull(page, "page");
    }

    public static ListMostBorrowedBooksQuery of(LocalDate from, LocalDate to, int page, int size) {
        return new ListMostBorrowedBooksQuery(from, to, new PageQuery(page, size, ORDER_FIELD, SortDirection.DESC));
    }
}
